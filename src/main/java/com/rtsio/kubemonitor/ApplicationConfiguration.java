package com.rtsio.kubemonitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtsio.kubemonitor.config.BaseMonitoringConfig;
import com.rtsio.kubemonitor.config.ClusterConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
public class ApplicationConfiguration {

    @Bean
    public BaseMonitoringConfig clusterConfig() {

        ObjectMapper objectMapper = new ObjectMapper();
        List<ClusterConfig> inactiveClusters = new ArrayList<>();
        try {
            InputStream jsonConfig = new ClassPathResource("monitoring-config.json").getInputStream();
            BaseMonitoringConfig config = objectMapper.readValue(jsonConfig, BaseMonitoringConfig.class);
            jsonConfig.close();
            for (ClusterConfig cluster : config.getClusters()) {
                if (!cluster.getEnabled()) {
                    log.info("Cluster {} for project {} was disabled via configuration", cluster.getName(), cluster.getProject());
                    inactiveClusters.add(cluster);
                }
            }
            config.getClusters().removeAll(inactiveClusters);
            log.info("Loaded cluster config");
            return config;
        } catch (Exception e) {
            log.error("Couldn't load cluster config", e);
            return null;
        }
    }

    @Bean
    public OkHttpClient httpClient() {

        return new OkHttpClient();
    }
}
