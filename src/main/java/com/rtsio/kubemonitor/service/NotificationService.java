package com.rtsio.kubemonitor.service;

import com.rtsio.kubemonitor.config.BaseMonitoringConfig;
import com.rtsio.kubemonitor.config.ClusterConfig;
import com.rtsio.kubemonitor.config.ClusterNotificationConfig;
import com.rtsio.kubemonitor.config.SlackWebhookConfig;
import com.rtsio.kubemonitor.model.*;
import com.rtsio.kubemonitor.model.MaintenanceRequest;
import com.rtsio.kubemonitor.model.MaintenanceState;
import com.rtsio.kubemonitor.model.MaintenanceStatus;
import com.rtsio.kubemonitor.slack.MessageSeverity;
import com.rtsio.kubemonitor.slack.SlackNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;



@Service
@Slf4j
public class NotificationService {

    @Autowired
    private BaseMonitoringConfig baseMonitoringConfig;

    @Autowired
    private SlackNotifier slackNotifier;

    public void notifyNewMaintenanceRequest(String project, String cluster, MaintenanceRequest maintenanceRequest) {

        ClusterConfig clusterConfig = findClusterConfig(project, cluster);
        if (!clusterConfig.getNotifications().getMaintenance().getEnabled()) {
            log.debug("Maintenance notifications for project {}, cluster {} are disabled; silencing notification", project, cluster);
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(String.format("New maintenance scheduled.\nProject: `%s`, cluster: `%s`\n", project, cluster));
        message.append("Start time: " + maintenanceRequest.getStartTime().toString() + "\n");
        message.append("End time: " + maintenanceRequest.getEndTime().toString() + "\n");
        message.append("Workloads to scale:\n");
        for (String workload : maintenanceRequest.getWorkloadsToScale()) {
            message.append(String.format("`%s`", workload) + "\n");
        }
        slackNotifier.sendMessage(message.toString(), findWebhook(clusterConfig.getNotifications().getMaintenance()).getUrl(), MessageSeverity.SUCCESS);
    }

    public void notifyMaintenanceUpdate(String project, String cluster, MaintenanceState maintenanceState) {

        ClusterConfig clusterConfig = findClusterConfig(project, cluster);
        if (!clusterConfig.getNotifications().getMaintenance().getEnabled()) {
            log.debug("Maintenance notifications for project {}, cluster {} are disabled; silencing notification", project, cluster);
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("Ran scheduled maintenance - " + (maintenanceState.getMaintenanceStatus() == MaintenanceStatus.STARTED? "shutdown of workloads.\n" : "scale-up of workloads.\n"));
        message.append(String.format("Project: `%s`, cluster: `%s`\n", maintenanceState.getMaintenanceRequest().getProject(), maintenanceState.getMaintenanceRequest().getCluster()));
        if (maintenanceState.getMaintenanceStatus() == MaintenanceStatus.STARTED) {
            message.append("Scaled down the following workloads:\n");
            for (String workload : maintenanceState.getOriginalReplicaCounts().keySet()) {
                message.append(String.format("`%s`", workload) + ", saved replica count: " + maintenanceState.getOriginalReplicaCounts().get(workload).toString() + "\n");
            }
            message.append("Workloads will be scaled back up at " + maintenanceState.getMaintenanceRequest().getEndTime().toString() + "\n");
        } else if (maintenanceState.getMaintenanceStatus() == MaintenanceStatus.ENDED) {
            message.append("Scaled up the following workloads to their original replica counts:\n");
            for (String workload : maintenanceState.getOriginalReplicaCounts().keySet()) {
                message.append(String.format("`%s`", workload) + "\n");
            }
            message.append("This maintenance is now finished!" + "\n");
        }
        slackNotifier.sendMessage(message.toString(), findWebhook(clusterConfig.getNotifications().getMaintenance()).getUrl(), MessageSeverity.SUCCESS);
    }

    public void notifyDeployment(String project, String cluster, String workloadName) {

        ClusterConfig clusterConfig = findClusterConfig(project, cluster);
        if (!clusterConfig.getNotifications().getDeployments().getEnabled()) {
            log.debug("Deployment notifications for project {}, cluster {} are disabled; silencing notification", project, cluster);
            return;
        }
        String text = String.format("`%s` is being deployed to cluster `%s` in project `%s`", workloadName, cluster, project);
        slackNotifier.sendMessage(text, findWebhook(clusterConfig.getNotifications().getDeployments()).getUrl(), MessageSeverity.SUCCESS);
    }

    public void notifyEvent(String project, String cluster, String text) {

        ClusterConfig clusterConfig = findClusterConfig(project, cluster);
        if (!clusterConfig.getNotifications().getEvents().getEnabled()) {
            log.debug("Event notifications for project {}, cluster {} are disabled; silencing notification", project, cluster);
            return;
        }
        slackNotifier.sendMessage(text, findWebhook(clusterConfig.getNotifications().getEvents()).getUrl(), MessageSeverity.ERROR);
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

    private SlackWebhookConfig findWebhook(ClusterNotificationConfig.NotificationConfig notificationConfig) {

        String webhookName = notificationConfig.getSlackWebhook();
        return baseMonitoringConfig
                .getSlackWebhookConfigs()
                .stream()
                .filter(slackWebhookConfig -> slackWebhookConfig.getName().equals(webhookName))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Could not find webhook " + webhookName));
    }
}
