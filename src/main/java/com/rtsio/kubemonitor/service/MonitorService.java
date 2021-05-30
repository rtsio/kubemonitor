package com.rtsio.kubemonitor.service;

import com.rtsio.kubemonitor.KubeUtils;
import com.rtsio.kubemonitor.config.ClusterConfig;
import com.rtsio.kubemonitor.config.BaseMonitoringConfig;
import com.rtsio.kubemonitor.exception.ClusterDoesNotExistException;
import com.rtsio.kubemonitor.model.ClusterState;
import com.rtsio.kubemonitor.model.ClusterStatus;
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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MonitorService {

    @Autowired
    private BaseMonitoringConfig baseMonitoringConfig;

    @Autowired
    private DeploymentTrackingService deploymentTrackingService;

    /**
     * Get state of the given cluster.
     * 1. Find configuration for project and cluster
     * 2. Call gcloud to make sure kubeconfig and cluster context exist (this is important to do every time as cluster IP may change between calls)
     * 3. Call gcloud to get oauth token (gcloud automatically refreshes if token is expired, otherwise it will serve up existing token)
     * 4. Compare configuration to cluster state using KubernetesClient, specifically looking for:
     *    - StatefulSets and Deployments where readyReplicas does not match replicas
     *    - StatefulSets and Deployments defined in configuration but not present in cluster
     */
    public ClusterStatus getClusterStatus(String project, String cluster) {

        log.info("Getting cluster status for project: {}, cluster: {}", project, cluster);
        ClusterConfig clusterConfig = findClusterConfig(project, cluster);
        List<String> deploymentsInProgress = deploymentTrackingService.getActiveDeploymentsForCluster(project, cluster);

        try {
            KubeUtils.setClusterKubeconfig(clusterConfig.getProject(), clusterConfig.getZone(), clusterConfig.getName());
        } catch (ClusterDoesNotExistException e) {
            return new ClusterStatus(project, cluster, ClusterState.DOWN, null, null);
        }

        String oauthToken = KubeUtils.getGcloudToken();
        String kubectlContext = KubeUtils.getGkeContextName(clusterConfig.getProject(), clusterConfig.getZone(), clusterConfig.getName());

        Config config = Config.autoConfigure(kubectlContext);
        config.setOauthToken(oauthToken);

        List<String> expectedStatefulSets = clusterConfig.getExpectedWorkloads().getStatefulSets();
        List<String> expectedDeployments = clusterConfig.getExpectedWorkloads().getDeployments();
        List<String> foundStatefulSets = new ArrayList<>();
        List<String> foundDeployments = new ArrayList<>();
        List<String> foundDiscrepancies = new ArrayList<>();

        // Loop through all deployments and statefulsets, noting any replica counts that appear out of sync
        try (KubernetesClient client = new DefaultKubernetesClient(config)) {

            List<StatefulSet> statefulSets = client.apps().statefulSets().inAnyNamespace().list().getItems();
            for (StatefulSet set : statefulSets) {
                String setName = set.getMetadata().getName();
                StatefulSetStatus setStatus = set.getStatus();
                Integer expectedReplicas = (setStatus.getReplicas() == null ? 0 : setStatus.getReplicas());
                Integer readyReplicas = (setStatus.getReadyReplicas() == null ? 0 : setStatus.getReadyReplicas());
                foundStatefulSets.add(setName);
                if (!expectedReplicas.equals(readyReplicas) && !deploymentsInProgress.contains(setName) && expectedStatefulSets.contains(setName)) {
                    foundDiscrepancies.add(String.format("StatefulSet %s expected %d replicas, but only %d ready", setName, expectedReplicas, readyReplicas));
                }
            }

            List<Deployment> deployments = client.apps().deployments().inAnyNamespace().list().getItems();
            for (Deployment deployment : deployments) {
                String deploymentName = deployment.getMetadata().getName();
                DeploymentStatus deploymentStatus = deployment.getStatus();
                Integer expectedReplicas = (deploymentStatus.getReplicas() == null ? 0 : deploymentStatus.getReplicas());
                Integer readyReplicas = (deploymentStatus.getReadyReplicas() == null ? 0 : deploymentStatus.getReadyReplicas());
                foundDeployments.add(deploymentName);
                if (!expectedReplicas.equals(readyReplicas) && !deploymentsInProgress.contains(deploymentName) && expectedDeployments.contains(deploymentName)) {
                    foundDiscrepancies.add(String.format("Deployment %s expected %d replicas, but only %d ready", deploymentName, expectedReplicas, readyReplicas));
                }
            }
        } catch (KubernetesClientException e) {
            throw new RuntimeException("Error in Kubernetes client", e);
        }

        // Compare found workloads vs expected to find things missing
        for (String statefulSet : expectedStatefulSets) {
            if (!foundStatefulSets.contains(statefulSet)) {
                foundDiscrepancies.add(String.format("StatefulSet %s not found in cluster %s", statefulSet, cluster));
            }
        }
        for (String deployment : expectedDeployments) {
            if (!foundDeployments.contains(deployment)) {
                foundDiscrepancies.add(String.format("Deployment %s not found in cluster %s", deployment, cluster));
            }
        }

        if (foundDiscrepancies.isEmpty()) {
            return new ClusterStatus(project, cluster, ClusterState.OK, new ArrayList<>(), deploymentsInProgress);
        } else {
            return new ClusterStatus(project, cluster, ClusterState.DEGRADED, foundDiscrepancies, deploymentsInProgress);
        }
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
