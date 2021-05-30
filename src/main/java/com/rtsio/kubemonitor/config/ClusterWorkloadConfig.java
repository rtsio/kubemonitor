package com.rtsio.kubemonitor.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ClusterWorkloadConfig {

    private List<String> deployments;
    @JsonProperty("stateful-sets")
    private List<String> statefulSets;
}
