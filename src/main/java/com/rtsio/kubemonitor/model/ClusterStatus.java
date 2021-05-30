package com.rtsio.kubemonitor.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ClusterStatus {

    String project;
    String cluster;
    ClusterState state;
    List<String> issues;
    List<String> deploymentsActive;
}
