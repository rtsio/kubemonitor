package com.rtsio.kubemonitor.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ClusterEventConfig {

    private Boolean enabled;
    private EventTypes types;

    @Data
    public static class EventTypes {

        @JsonProperty("readiness-probe")
        public Boolean readinessProbe;
        @JsonProperty("liveness-probe")
        public Boolean livenessProbe;
        @JsonProperty("oom-kill")
        public Boolean outOfMemoryKill;
    }
}
