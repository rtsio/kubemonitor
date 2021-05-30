package com.rtsio.kubemonitor.model;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class MaintenanceRequest {

    private String project;
    private String cluster;
    private Instant startTime;
    private Instant endTime;
    private List<String> workloadsToScale;
}
