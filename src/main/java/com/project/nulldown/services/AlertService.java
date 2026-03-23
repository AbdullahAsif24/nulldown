package com.project.nulldown.services;

import com.project.nulldown.event.MonitorDownEvent;
import com.project.nulldown.event.MonitorUpEvent;
import com.project.nulldown.model.Monitor;
import com.project.nulldown.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final StringRedisTemplate redis;
    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @EventListener
    public void onMonitorDown(MonitorDownEvent event) {
        String key = "alert:down:" + event.monitor().getId();

        Boolean isNew = redis.opsForValue()
                .setIfAbsent(key, "1", Duration.ofMinutes(10));

        if (Boolean.TRUE.equals(isNew)) {
            log.info("ALERT: {} is DOWN! Sending email...", event.monitor().getUrl());
            sendDownEmail(event.monitor());
        } else {
            log.info("Alert already sent for {} — skipping", event.monitor().getUrl());
        }
    }

    @EventListener
    public void onMonitorUp(MonitorUpEvent event) {
        log.info("RECOVERY: {} is back UP!", event.monitor().getUrl());
        sendRecoveryEmail(event.monitor());
    }

    private String getUserEmail(Monitor monitor) {
        return userRepository.findById(monitor.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getEmail();
    }

    private void sendDownEmail(Monitor monitor) {
        String to = getUserEmail(monitor);

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("🚨 DOWN: " + monitor.getName());
        msg.setText(
            "Your site is DOWN!\n\n" +
            "Name: " + monitor.getName() + "\n" +
            "URL: " + monitor.getUrl() + "\n" +
            "Time: " + LocalDateTime.now()
        );
        mailSender.send(msg);
        log.info("Down email sent to {}", to);
    }

    private void sendRecoveryEmail(Monitor monitor) {
        String to = getUserEmail(monitor);

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("✅ RECOVERED: " + monitor.getName());
        msg.setText(
            "Your site is back UP!\n\n" +
            "Name: " + monitor.getName() + "\n" +
            "URL: " + monitor.getUrl() + "\n" +
            "Time: " + LocalDateTime.now()
        );
        mailSender.send(msg);
        log.info("Recovery email sent to {}", to);
    }
}