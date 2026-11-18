package ru.vacancybot.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "worker.events")
public record EventsProperties(
        @DefaultValue("vacancy.events") String queue,
        @DefaultValue("200") int batchSize,
        @DefaultValue("10s") Duration confirmTimeout
) {
    public EventsProperties {
        if (queue == null || queue.isBlank()) {
            throw new IllegalArgumentException("worker.events.queue must not be blank");
        }
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("worker.events.batch-size must be between 1 and 1000");
        }
        if (confirmTimeout.isNegative() || confirmTimeout.isZero()) {
            throw new IllegalArgumentException("worker.events.confirm-timeout must be positive");
        }
    }
}