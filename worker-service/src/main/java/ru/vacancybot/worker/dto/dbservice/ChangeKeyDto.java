package ru.vacancybot.worker.dto.dbservice;

import java.time.Instant;

public record ChangeKeyDto(Long changeId, Instant changedAt) {

    public static ChangeKeyDto of(VacancyChangeDto change) {
        return new ChangeKeyDto(change.changeId(), change.changedAt());
    }
}