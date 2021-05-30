package com.rtsio.kubemonitor.service;

import com.rtsio.kubemonitor.KubeUtils;
import com.rtsio.kubemonitor.config.BaseMonitoringConfig;
import com.rtsio.kubemonitor.config.ClusterConfig;
import com.rtsio.kubemonitor.exception.ClusterDoesNotExistException;
import com.rtsio.kubemonitor.model.*;
import com.rtsio.kubemonitor.model.MaintenanceRequest;
import com.rtsio.kubemonitor.model.MaintenanceState;
import com.rtsio.kubemonitor.model.MaintenanceStatus;
import io.fabric8.kubernetes.api.model.Cluster;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatus;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MaintenanceService {

    @Autowired
    private BaseMonitoringConfig baseMonitoringConfig;

    @Autowired
    private NotificationService notificationService;

    private List<MaintenanceState> maintenanceStates = new ArrayList<>();

    public void scheduleMaintenance(MaintenanceRequest maintenanceRequest) {

        MaintenanceState newMaintenance = new MaintenanceState(maintenanceRequest);
        maintenanceStates.add(newMaintenance);
        notificationService.notifyNewMaintenanceRequest(maintenanceRequest.getProject(), maintenanceRequest.getCluster(), maintenanceRequest);
    }

    /**
     * This is a (frankly hilarious) hack around running something like Quartz and scheduling triggers at the specified time
     */
    @Scheduled(fixedDelay = 10000)
    public void checkMaintenanceRequests() {

        Instant now = Instant.now();
        for (MaintenanceState maintenance : maintenanceStates) {
            try {
                if (maintenance.getMaintenanceStatus() == MaintenanceStatus.NOT_STARTED && now.compareTo(maintenance.getMaintenanceRequest().getStartTime()) >= 0) {
                    runMaintenanceTask(maintenance);
                }
                if (maintenance.getMaintenanceStatus() == MaintenanceStatus.STARTED && now.compareTo(maintenance.getMaintenanceRequest().getEndTime()) >= 0) {
                    runMaintenanceTask(maintenance);
                }
            } catch (Exception e) {
                log.error("Exception while executing maintenance", e);
                maintenance.setMaintenanceStatus(MaintenanceStatus.ERROR);
            }
        }
    }

    private void runMaintenanceTask(MaintenanceState maintenanceState) {

        String project = maintenanceState.getMaintenanceRequest().getProject();
        String cluster = maintenanceState.getMaintenanceRequest().getCluster();
        ClusterConfig clusterConfig = findClusterConfig(project, cluster);
        List<String> maintenanceWorkloads = maintenanceState.getMaintenanceRequest().getWorkloadsToScale();
        MaintenanceStatus lifecycle = maintenanceState.getMaintenanceStatus();
        log.info("Running maintenance task for project: {}, cluster: {}, current maintenance status: {}", project, cluster, maintenanceState.getMaintenanceStatus());

        try {
            KubeUtils.setClusterKubeconfig(project, clusterConfig.getZone(), cluster);
        } catch (ClusterDoesNotExistException e) {
            log.error("Could not run maintenance task for project: {}, cluster: {}, as the cluster doesn't exist!", project, cluster);
            return;
        }
        String oauthToken = KubeUtils.getGcloudToken();
        String kubectlContext = KubeUtils.getGkeContextName(project, clusterConfig.getZone(), cluster);
        log.debug("Setting context to {}", kubectlContext);

        Config config = Config.autoConfigure(kubectlContext);
        config.setOauthToken(oauthToken);

        /**
         * Scan all StatefulSets/Deployments;
         *   If maintenanceState == NOT_STARTED, record original replica counts and scale to 0
         *   If maintenanceState == STARTED, scale to original replica counts
         */
        try (KubernetesClient client = new DefaultKubernetesClient(config)) {

            List<StatefulSet> statefulSets = client.apps().statefulSets().inAnyNamespace().list().getItems();
            for (StatefulSet set : statefulSets) {
                String setName = set.getMetadata().getName();
                String setNamespace = set.getMetadata().getNamespace();
                StatefulSetStatus setStatus = set.getStatus();
                Integer expectedReplicas = (setStatus.getReplicas() == null ? 0 : setStatus.getReplicas());
                Integer readyReplicas = (setStatus.getReadyReplicas() == null ? 0 : setStatus.getReadyReplicas());
                log.debug("Found stateful set {} in namespace {}, ready: {}, expected: {}", setName, setNamespace, readyReplicas, expectedReplicas);
                if (maintenanceWorkloads.contains(setName)) {
                    if (lifecycle == MaintenanceStatus.NOT_STARTED) {
                        maintenanceState.getOriginalReplicaCounts().put(setName, readyReplicas);
                        client.apps().statefulSets().inNamespace(setNamespace).withName(setName).scale(0);
                        log.debug("Scaled set {} to 0", setName);
                    } else if (lifecycle == MaintenanceStatus.STARTED) {
                        Integer originalReplicas = maintenanceState.getOriginalReplicaCounts().get(setName);
                        client.apps().statefulSets().inNamespace(setNamespace).withName(setName).scale(originalReplicas);
                        log.debug("Scaled set {} to {}", setName, originalReplicas);
                    }
                }
            }

            List<Deployment> deployments = client.apps().deployments().inAnyNamespace().list().getItems();
            for (Deployment deployment : deployments) {
                String deploymentName = deployment.getMetadata().getName();
                String deploymentNamespace = deployment.getMetadata().getNamespace();
                DeploymentStatus deploymentStatus = deployment.getStatus();
                Integer expectedReplicas = (deploymentStatus.getReplicas() == null ? 0 : deploymentStatus.getReplicas());
                Integer readyReplicas = (deploymentStatus.getReadyReplicas() == null ? 0 : deploymentStatus.getReadyReplicas());
                log.debug("Found deployment {} in namespace {}, ready: {}, expected: {}", deploymentName, deploymentNamespace, readyReplicas, expectedReplicas);
                if (maintenanceWorkloads.contains(deploymentName)) {
                    if (lifecycle == MaintenanceStatus.NOT_STARTED) {
                        maintenanceState.getOriginalReplicaCounts().put(deploymentName, readyReplicas);
                        client.apps().deployments().inNamespace(deploymentNamespace).withName(deploymentName).scale(0);
                        log.debug("Scaled deployment {} to 0", deploymentName);
                    } else if (lifecycle == MaintenanceStatus.STARTED) {
                        Integer originalReplicas = maintenanceState.getOriginalReplicaCounts().get(deploymentName);
                        client.apps().deployments().inNamespace(deploymentNamespace).withName(deploymentName).scale(originalReplicas);
                        log.debug("Scaled deployment {} to {}", deploymentName, originalReplicas);
                    }
                }
            }

        } catch (KubernetesClientException e) {
            throw new RuntimeException("Error in Kubernetes client", e);
        }

        if (maintenanceState.getMaintenanceStatus() == MaintenanceStatus.NOT_STARTED) {
            maintenanceState.setMaintenanceStatus(MaintenanceStatus.STARTED);
        } else if (maintenanceState.getMaintenanceStatus() == MaintenanceStatus.STARTED) {
            maintenanceState.setMaintenanceStatus(MaintenanceStatus.ENDED);
        }
        notificationService.notifyMaintenanceUpdate(project, cluster, maintenanceState);
        log.info("Finished running maintenance task for project: {}, cluster: {}, new maintenance status: {}", project, cluster, maintenanceState.getMaintenanceStatus());
    }

    /**
     * Find configuration for given project and cluster name
     */
    private ClusterConfig findClusterConfig(String projectName, String clusterName) {

        return baseMonitoringConfig
                .getClusters()
                .stream()
                .filter(clusterConfig -> clusterConfig.getProject().equals(projectName) && clusterConfig.getName().equals(clusterName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Could not find " + clusterName + " in " + projectName));
    }
}
