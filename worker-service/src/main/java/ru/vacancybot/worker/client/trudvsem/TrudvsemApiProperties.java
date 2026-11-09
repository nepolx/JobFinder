package ru.vacancybot.worker.client.trudvsem;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "worker.trudvsem")
public record TrudvsemApiProperties(
        String baseUrl,
        int pageSize,
        Duration connectTimeout,
        Duration readTimeout
) {}