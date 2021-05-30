package com.rtsio.kubemonitor.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


@Data
public class ClusterConfig {

    private String project;
    private String name;
    private String zone;
    private Boolean enabled;
    private ClusterEventConfig events;
    private ClusterNotificationConfig notifications;
    @JsonProperty("expected-workloads")
    private ClusterWorkloadConfig expectedWorkloads;
}
