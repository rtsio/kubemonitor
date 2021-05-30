package com.rtsio.kubemonitor.watcher;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class EventCache {

    private Map<String, Instant> cache = new HashMap<>();
    private final static Integer EXPIRATION_MINUTES = 5;

    public boolean hasCached(String messageKey) {

        if (!cache.containsKey(messageKey)) {
            cacheEvent(messageKey);
            return false;
        } else {
            if (Instant.now().compareTo(cache.get(messageKey)) > 0) {
                cache.remove(messageKey);
                // The desired behavior here is "return false at most once every X minutes"
                cacheEvent(messageKey);
                return false;
            }
            return true;
        }
    }

    private void cacheEvent(String messageKey) {

        cache.put(messageKey, Instant.now().plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES));
    }

    @Scheduled(fixedDelay = 3600000)
    private void clearCache() {

        cache.entrySet().removeIf(event -> (Instant.now().compareTo(event.getValue()) > 0));
    }
}
