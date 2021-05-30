package com.rtsio.kubemonitor.controller;

import com.rtsio.kubemonitor.config.BaseMonitoringConfig;
import com.rtsio.kubemonitor.model.ClusterStatus;
import com.rtsio.kubemonitor.model.DetectedDeployment;
import com.rtsio.kubemonitor.service.DeploymentTrackingService;
import com.rtsio.kubemonitor.service.MonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@Slf4j
public class MonitorController {

    @Autowired
    private BaseMonitoringConfig baseMonitoringConfig;

    @Autowired
    private MonitorService monitorService;

    @Autowired
    private DeploymentTrackingService deploymentTrackingService;

    @GetMapping("/config")
    public BaseMonitoringConfig getConfig() {

        return baseMonitoringConfig;
    }

    @GetMapping("/status")
    public ClusterStatus getStatus(@RequestParam String project, @RequestParam String cluster) throws IOException, InterruptedException {

        if (project == null || cluster == null) {
            throw new RuntimeException("Cluster and project cannot be null");
        }
        return monitorService.getClusterStatus(project, cluster);
    }

    @GetMapping("/deployments")
    public List<DetectedDeployment> getDeployments() {

        return deploymentTrackingService.getAll();
    }
}