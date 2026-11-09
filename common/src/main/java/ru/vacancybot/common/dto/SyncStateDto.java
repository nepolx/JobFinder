package ru.vacancybot.common.dto;

import java.time.Instant;

/** Значение синхронизации (ответ GET/PUT /api/sync-state/{key}). */
public record SyncStateDto(
        String key,
        String value,
        Instant updatedAt
) {}