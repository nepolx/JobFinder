package ru.vacancybot.worker.client.dbservice;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "worker.db-service")
public record DbServiceProperties(
        @DefaultValue("http://localhost:8081") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout
) {}