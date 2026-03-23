package com.project.nulldown.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.nulldown.dto.MonitorRequest;
import com.project.nulldown.dto.MonitorResponse;
import com.project.nulldown.dto.MonitorStats;
import com.project.nulldown.dto.ProfileResponse;
import com.project.nulldown.model.User;
import com.project.nulldown.repository.UserRepository;
import com.project.nulldown.services.MonitorService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorService monitorService;
    private final UserRepository userRepository;

    @PostMapping("/create")
    public ResponseEntity<MonitorResponse> create(@RequestBody MonitorRequest request) {
        // Implementation for creating a monitor

        return ResponseEntity.status(201).body(monitorService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<MonitorResponse>> getAll() {
        return ResponseEntity.ok(monitorService.getAll());
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<MonitorStats> getStats(@PathVariable UUID id) {
        return ResponseEntity.ok(monitorService.getStats(id));
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> me() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(new ProfileResponse(user.getId(), user.getEmail()));
    }
}
