package ru.vacancybot.common.dto;

import java.time.Instant;

/** Ключ события: пара (changeId, changedAt) из ответа POST /api/vacancies/sync. */
public record ChangeKeyDto(
        Long changeId,
        Instant changedAt
) {}