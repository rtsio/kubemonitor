package com.rtsio.kubemonitor.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class DetectedDeployment {

    private String project;
    private String cluster;
    private String workloadName;
    private Instant expiration;
}
