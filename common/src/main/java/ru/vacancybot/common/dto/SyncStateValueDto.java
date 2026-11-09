package ru.vacancybot.common.dto;

/** Тело PUT /api/sync-state/{key}. */
public record SyncStateValueDto(
        String value
) {}