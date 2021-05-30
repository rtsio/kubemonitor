package com.rtsio.kubemonitor.watcher;

import com.rtsio.kubemonitor.KubeUtils;
import com.rtsio.kubemonitor.config.ClusterConfig;
import com.rtsio.kubemonitor.exception.ClusterDoesNotExistException;
import com.rtsio.kubemonitor.service.NotificationService;
import com.rtsio.kubemonitor.service.DeploymentTrackingService;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

@Data
@Slf4j
public class ClusterEventWatcher {

    private DeploymentTrackingService deploymentTrackingService;
    private NotificationService notificationService;
    private EventCache eventCache;

    private ClusterConfig clusterConfig;
    private KubernetesClient client;
    private Watch watch;

    public ClusterEventWatcher(ClusterConfig clusterConfig,
                               DeploymentTrackingService deploymentTrackingService,
                               NotificationService notificationService,
                               EventCache eventCache) {

        this.clusterConfig = clusterConfig;
        this.deploymentTrackingService = deploymentTrackingService;
        this.notificationService = notificationService;
        this.eventCache = eventCache;
    }

    public void initWatcher() {

        try {
            KubeUtils.setClusterKubeconfig(clusterConfig.getProject(), clusterConfig.getZone(), clusterConfig.getName());
        } catch (ClusterDoesNotExistException e) {
            log.warn("Could not init watcher for project: {}, cluster: {}, as the cluster doesn't exist!", clusterConfig.getProject(), clusterConfig.getName());
            return;
        }
        String oauthToken = KubeUtils.getGcloudToken();
        String kubectlContext = KubeUtils.getGkeContextName(clusterConfig.getProject(), clusterConfig.getZone(), clusterConfig.getName());
        Config config = Config.autoConfigure(kubectlContext);
        config.setOauthToken(oauthToken);
        Instant startTime = Instant.now();
        UUID watcherUuid = UUID.randomUUID();
        String watcherShortId = watcherUuid.toString().substring(0, 6);
        log.info("Creating watcher for {} - {}, ID: {}", clusterConfig.getProject(), clusterConfig.getName(), watcherShortId);

        client = new DefaultKubernetesClient(config);
        watch = client.v1().events().inAnyNamespace().watch(new Watcher<Event>() {

            @Override
            public void eventReceived(Action action, Event resource) {

                // Filter on timestamp to drop events that occurred before watch started
                Instant eventTimestamp = ZonedDateTime.parse(resource.getLastTimestamp()).toInstant();
                if (eventTimestamp.compareTo(startTime) < 0) {
                    log.debug("[{}] [{}] Dropped event occurring before polling started, {}", clusterConfig.getProject(), watcherShortId, resource);
                    return;
                }
                log.debug("[{}] [{}] Received event: {}", clusterConfig.getProject(), watcherShortId, resource);
                parseEvent(resource);
            }

            @Override
            public void onClose(KubernetesClientException e) {

                if (e == null) {
                    return;
                }
                // Handle "resourceVersion for the provided watch is too old" exceptions = HTTP_GONE
                if (e.getMessage().contains("resourceVersion")) {
                    log.debug("[{}] [{}] resourceVersion too old for watch, reconnecting", clusterConfig.getProject(), watcherShortId);
                    client.close();
                    initWatcher();
                } else {
                    log.error("Unexpected error on watch close", e);
                }
            }
        });
    }

    public void closeWatcher() {

        watch.close();
        client.close();
    }

    public void parseEvent(Event resource) {

        ObjectReference involvedObject = resource.getInvolvedObject();
        String kind = involvedObject.getKind();
        String name = involvedObject.getName();
        String reason = resource.getReason();
        String type = resource.getType();
        if (type.equals("Normal")) {
            if (kind.equals("Deployment") && reason.equals("ScalingReplicaSet")) {
                deploymentTrackingService.addDeployment(clusterConfig.getProject(), clusterConfig.getName(), name);
            }
            if (kind.equals("StatefulSet") && reason.equals("SuccessfulCreate")) {
                deploymentTrackingService.addDeployment(clusterConfig.getProject(), clusterConfig.getName(), name);
            }
            if (reason.equals("Killing")) {
                if (resource.getMessage().contains("failed liveness probe") || resource.getMessage().contains("failed readiness probe")) {
                    String message = String.format("%s: %s - %s", clusterConfig.getProject(), resource.getMessage(), name);
                    notificationService.notifyEvent(clusterConfig.getProject(), clusterConfig.getName(), message);
                }
            }
        } else if (type.equals("Warning")) {
            if (reason.equals("Unhealthy")) {
                if (eventCache.hasCached(resource.getMetadata().getUid())) {
                    log.debug("Event with message: \"{}\" already cached, ignoring", resource.getMessage());
                } else {
                    if (resource.getMessage().contains("Liveness")) {
                        if (clusterConfig.getEvents().getTypes().getLivenessProbe()) {
                            String message = String.format("%s: %s - %s", clusterConfig.getProject(), resource.getMessage(), name);
                            notificationService.notifyEvent(clusterConfig.getProject(), clusterConfig.getName(), message);
                        }
                    } else if (resource.getMessage().contains("Readiness")) {
                        if (clusterConfig.getEvents().getTypes().getReadinessProbe()) {
                            String message = String.format("%s: %s - %s", clusterConfig.getProject(), resource.getMessage(), name);
                            notificationService.notifyEvent(clusterConfig.getProject(), clusterConfig.getName(), message);
                        }
                    }
                }
            } else if (reason.equals("OOMKilling")) {
                if (clusterConfig.getEvents().getTypes().getOutOfMemoryKill()) {
                    String message = String.format("%s: OOM kill on node %s", clusterConfig.getProject(), name);
                    notificationService.notifyEvent(clusterConfig.getProject(), clusterConfig.getName(), message);
                }
            }
        }
    }
}
