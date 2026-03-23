package com.project.nulldown.schedule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.project.nulldown.event.MonitorDownEvent;
import com.project.nulldown.event.MonitorUpEvent;
import com.project.nulldown.model.Monitor;
import com.project.nulldown.model.PingLog;
import com.project.nulldown.repository.MonitorRepository;
import com.project.nulldown.repository.PingLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Component
@Slf4j
public class MonitorPinger {

    private final MonitorRepository monitorRepository;
    private final PingLogRepository pingLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate redis;

    @Scheduled(fixedDelay = 60000)
    public void pingAll() {
        List<Monitor> monitors = monitorRepository.findAllByActiveTrue();
        log.info("Pinging {} monitors", monitors.size());
        monitors.forEach(this::ping);
    }

    private void ping(Monitor monitor) {
        try {
            long start = System.currentTimeMillis();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(monitor.getUrl()))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request,
                    HttpResponse.BodyHandlers.discarding());
            int ms = (int) (System.currentTimeMillis() - start);

            savePingLog(monitor.getId(), monitor, "UP", ms, response.statusCode());

        } catch (Exception e) {
            savePingLog(monitor.getId(), monitor, "DOWN", -1, 0);
        }
    }

    private void savePingLog(UUID monitorId, Monitor monitor, String status, int ms, int statusCode) {
        PingLog pinglog = new PingLog();
        pinglog.setMonitorId(monitorId);
        pinglog.setStatus(status);
        pinglog.setResponseMs(ms);
        pinglog.setStatusCode(statusCode);
        pingLogRepository.save(pinglog);

        String key = "alert:down:" + monitorId;

        if ("DOWN".equals(status)) {
            eventPublisher.publishEvent(new MonitorDownEvent(monitor));
            log.info("Event fired: monitor DOWN -> {}", monitor.getUrl());
        }

        if ("UP".equals(status)) {
            // was it previously down?
            if (Boolean.TRUE.equals(redis.hasKey(key))) {
                redis.delete(key); // incident over
                eventPublisher.publishEvent(new MonitorUpEvent(monitor));
                log.info("Event fired: monitor RECOVERED -> {}", monitor.getUrl());
            }
        }

        trimOldLogs(monitorId);

    }

    private void trimOldLogs(UUID monitorId) {
        List<PingLog> logs = pingLogRepository
                .findByMonitorIdOrderByCheckedAtDesc(monitorId);

        if (logs.size() > 100) {
            List<PingLog> toDelete = logs.subList(100, logs.size());
            pingLogRepository.deleteAll(toDelete);
        }
    }
}
