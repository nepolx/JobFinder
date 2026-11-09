package ru.vacancybot.common.dto;

import java.time.Instant;

/** Тело POST /api/changes/publish-failed: событие не удалось опубликовать. */
public record PublishFailedDto(
        Long changeId,
        Instant changedAt,
        String error
) {}