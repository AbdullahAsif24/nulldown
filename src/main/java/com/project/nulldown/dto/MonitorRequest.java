package com.project.nulldown.dto;

import lombok.Data;

@Data
public class MonitorRequest {
    
    private String name;
    private String url;
    private int intervalSec = 60;
}
