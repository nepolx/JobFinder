package ru.vacancybot.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "worker.sync")
public record SyncProperties(
        @DefaultValue("1h") Duration windowSize,
        @DefaultValue("2") int maxReloadAttempts,
        @DefaultValue("3") int maxRetries,
        @DefaultValue("1s") Duration retryBaseDelay,
        @DefaultValue("100ms") Duration pauseBetweenRequests
) {
    public SyncProperties {
        if (windowSize.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("worker.sync.window-size must be at least 1 second");
        }
        if (maxReloadAttempts < 0 || maxRetries < 0) {
            throw new IllegalArgumentException("worker.sync retry counters must not be negative");
        }
        if (retryBaseDelay.isNegative() || pauseBetweenRequests.isNegative()) {
            throw new IllegalArgumentException("worker.sync delays must not be negative");
        }
    }
}