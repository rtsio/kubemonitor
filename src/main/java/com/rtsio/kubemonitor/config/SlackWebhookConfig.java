package com.rtsio.kubemonitor.config;

import lombok.Data;

@Data
public class SlackWebhookConfig {

    private String name;
    private String url;
}
