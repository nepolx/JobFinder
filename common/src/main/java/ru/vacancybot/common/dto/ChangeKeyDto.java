package ru.vacancybot.common.dto;

import java.time.Instant;

/** Ключ события: пара (changeId, changedAt) из ответа POST /api/vacancies/sync. */
public record ChangeKeyDto(
        Long changeId,
        Instant changedAt
) {
    public static ChangeKeyDto of(VacancyChangeDto change) {
        return new ChangeKeyDto(change.changeId(), change.changedAt());
    }
}