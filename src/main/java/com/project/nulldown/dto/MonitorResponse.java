package com.project.nulldown.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MonitorResponse {
    
    private UUID id;
    private String name;
    private String url;
    private boolean active;
    private LocalDateTime createdAt;
}
