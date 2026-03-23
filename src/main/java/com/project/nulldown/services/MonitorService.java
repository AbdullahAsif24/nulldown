package com.project.nulldown.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.project.nulldown.dto.MonitorRequest;
import com.project.nulldown.dto.MonitorResponse;
import com.project.nulldown.dto.MonitorStats;
import com.project.nulldown.model.Monitor;
import com.project.nulldown.model.PingLog;
import com.project.nulldown.repository.MonitorRepository;
import com.project.nulldown.repository.PingLogRepository;
import com.project.nulldown.repository.UserRepository;
import com.project.nulldown.security.JwtUtil;

import org.springframework.security.core.context.SecurityContextHolder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MonitorService {

    private final MonitorRepository monitorRepository;
    private final PingLogRepository pingLogRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public MonitorResponse create(MonitorRequest request) {
        UUID userId = getCurrentUserId();

        Monitor monitor = new Monitor();
        monitor.setName(request.getName());
        monitor.setUrl(request.getUrl());
        monitor.setIntervalSec(request.getIntervalSec());
        monitor.setUserId(userId);

        Monitor saved = monitorRepository.save(monitor);
        return toResponse(saved);
    }

    public List<MonitorResponse> getAll() {
        UUID userId = getCurrentUserId();
        return monitorRepository.findAllByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MonitorStats getStats(UUID monitorId) {
        List<PingLog> logs = pingLogRepository.findByMonitorId(monitorId);

        if (logs.isEmpty()) {
            return new MonitorStats(100.0, 0, 0, 0);
        }

        long total = logs.size();
        long upCount = logs.stream()
                .filter(l -> "UP".equals(l.getStatus()))
                .count();

        double uptimePct = (upCount * 100.0) / total;

        int avgResponse = (int) logs.stream()
                .filter(l -> l.getResponseMs() > 0)
                .mapToInt(PingLog::getResponseMs)
                .average()
                .orElse(0);

        return new MonitorStats(uptimePct, avgResponse, total, upCount);
    }

    private UUID getCurrentUserId() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }

    private MonitorResponse toResponse(Monitor monitor) {
    return MonitorResponse.builder()
            .id(monitor.getId())
            .name(monitor.getName())
            .url(monitor.getUrl())
            .active(monitor.isActive())
            .createdAt(monitor.getCreatedAt())
            .build();
}
}