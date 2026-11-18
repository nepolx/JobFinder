package ru.vacancybot.worker.dto.dbservice;

import java.time.Instant;

public record SyncStateDto(String key, String value, Instant updatedAt) {}