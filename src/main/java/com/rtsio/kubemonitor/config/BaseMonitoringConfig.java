package com.rtsio.kubemonitor.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BaseMonitoringConfig {

    private List<ClusterConfig> clusters;

    @JsonProperty("slack-webhooks")
    private List<SlackWebhookConfig> slackWebhookConfigs;
}
