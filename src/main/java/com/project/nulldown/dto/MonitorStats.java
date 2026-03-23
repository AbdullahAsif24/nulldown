package com.project.nulldown.dto;

public record MonitorStats(
        double uptimePercentage,
        int avgResponseMs,
        long totalPings,
        long upPings
) {}