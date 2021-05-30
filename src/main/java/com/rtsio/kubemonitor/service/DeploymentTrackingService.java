package com.rtsio.kubemonitor.service;

import com.rtsio.kubemonitor.model.DetectedDeployment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Deployment as in: the action of deploying new code to a cluster, not the Kubernetes workload type
 */
@Service
@Slf4j
public class DeploymentTrackingService {

    @Autowired
    private NotificationService notificationService;

    private List<DetectedDeployment> knownDeployments = new ArrayList<>();

    /**
     * Add a workload for given project/cluster to a list, with an "expiration" time.
     * Once expired (current time > expiration timestamp), a deployment is considered finished.
     * This is a simple way to avoid listening for events that signify deployment is done, as these
     * don't really exist anyway.
     */
    public void addDeployment(String project, String cluster, String workloadName) {

        log.info("Received request to add deployment of {} to {}, {}", workloadName, project, cluster);
        // TODO: configurable deployment duration (long startup services)
        DetectedDeployment newDeployment = new DetectedDeployment(
                project,
                cluster,
                workloadName,
                Instant.now().plus(3, ChronoUnit.MINUTES)
        );

        // Multiple events can be emitted for one logical deployment - if there's an "active deployment", ignore these
        // TODO: remove deployments instead of continually adding them and filtering - how to do this in a thread-safe manner?
        Boolean canAdd = true;
        for (DetectedDeployment previousDeployment : knownDeployments) {
            if (previousDeployment.getCluster().equals(cluster) &&
                    previousDeployment.getProject().equals((project)) &&
                    previousDeployment.getWorkloadName().equals(workloadName)) {
                if (Instant.now().compareTo(previousDeployment.getExpiration()) > 0) {
                    log.debug("{} is considered expired, can add a new deployment", previousDeployment);
                } else {
                    log.debug("{} is currently active, ignoring new event", previousDeployment);
                    canAdd = false;
                }
            }
        }
        if (canAdd) {
            log.info("Added {} to current deployments", newDeployment);
            knownDeployments.add(newDeployment);
            notificationService.notifyDeployment(project, cluster, workloadName);
        }
    }

    public List<String> getActiveDeploymentsForCluster(String project, String cluster) {

        List<String> deployments = new ArrayList<>();
        for (DetectedDeployment detectedDeployment : knownDeployments) {
            if (detectedDeployment.getCluster().equals(cluster) && detectedDeployment.getProject().equals((project))) {
                if (Instant.now().compareTo(detectedDeployment.getExpiration()) > 0) {
                    log.debug("{} is considered expired, ignoring", detectedDeployment);
                } else {
                    log.debug("{} is currently active, returning", detectedDeployment);
                    deployments.add(detectedDeployment.getWorkloadName());
                }
            }
        }
        return deployments;
    }

    public List<DetectedDeployment> getAll() {

        return knownDeployments;
    }
}
