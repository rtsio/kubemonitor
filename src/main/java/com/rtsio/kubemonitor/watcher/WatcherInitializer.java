package com.rtsio.kubemonitor.watcher;

import com.rtsio.kubemonitor.config.ClusterConfig;
import com.rtsio.kubemonitor.config.BaseMonitoringConfig;
import com.rtsio.kubemonitor.service.NotificationService;
import com.rtsio.kubemonitor.service.DeploymentTrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Component
@Slf4j
public class WatcherInitializer {

    @Value("${watchers.enabled}")
    private Boolean enabled;

    @Autowired
    private BaseMonitoringConfig baseMonitoringConfig;

    @Autowired
    private DeploymentTrackingService deploymentTrackingService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EventCache eventCache;

    private List<ClusterEventWatcher> watcherList = new ArrayList<>();

    /**
     * Start a Watch for each cluster where configured, and add to list.
     * Regularly restart existing Watches due to fabric8 library not refreshing OAuth tokens
     * properly; if not done, KubernetesClient/Watch starts receiving HTTP 401.
     * 3300000 = 55 minutes (OAuth token expires every 60m)
     */
    @Scheduled(fixedRate = 3300000, initialDelay = 5000)
    public void startWatchers() {

        if (enabled) {
            for (ClusterConfig clusterConfig : baseMonitoringConfig.getClusters()) {
                if (clusterConfig.getEvents().getEnabled()) {


                    ClusterEventWatcher watcher = watcherExistsForCluster(clusterConfig);
                    if (watcher == null) {
                        // First time creation on startup
                        ClusterEventWatcher clusterEventWatcher = new ClusterEventWatcher(clusterConfig,
                                deploymentTrackingService,
                                notificationService,
                                eventCache);
                        clusterEventWatcher.initWatcher();
                        watcherList.add(clusterEventWatcher);
                    } else {
                        // Regular restart
                        log.info("Restarting watcher for {} - {}", clusterConfig.getProject(), clusterConfig.getName());
                        watcher.closeWatcher();
                        watcher.initWatcher();
                    }
                }
            }
        }
    }

    private ClusterEventWatcher watcherExistsForCluster(ClusterConfig clusterConfig) {

        return watcherList
                .stream()
                .filter(watcher -> watcher.getClusterConfig().getName().equals(clusterConfig.getName()) && watcher.getClusterConfig().getProject().equals(clusterConfig.getProject()))
                .findAny().orElse(null);
    }
}
