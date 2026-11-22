package ru.vacancybot.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "worker.run")
public record RunProperties(
        @DefaultValue("2h") Duration overlap,
        @DefaultValue("24h") Duration initialLookback,
        @DefaultValue("48h") Duration maxCatchUp
) {
    public RunProperties {
        if (overlap.isNegative()) {
            throw new IllegalArgumentException("worker.run.overlap must not be negative");
        }
        if (maxCatchUp.isNegative() || maxCatchUp.isZero()) {
            throw new IllegalArgumentException("worker.run.max-catch-up must be positive");
        }
        if (initialLookback.isNegative() || initialLookback.isZero()) {
            throw new IllegalArgumentException("worker.run.initial-lookback must be positive");
        }
    }
}