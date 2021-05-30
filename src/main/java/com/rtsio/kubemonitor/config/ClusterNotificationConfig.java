package com.rtsio.kubemonitor.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ClusterNotificationConfig {

    private NotificationConfig events;
    private NotificationConfig deployments;
    private NotificationConfig maintenance;

    @Data
    public static class NotificationConfig {

        public Boolean enabled;
        @JsonProperty("slack-webhook")
        public String slackWebhook;
    }
}
