package ru.vacancybot.worker.dto.dbservice;

import java.time.Instant;

public record ChangeKey(long changeId, Instant changedAt) {

    public static ChangeKey of(VacancyChangeDto change) {
        return new ChangeKey(change.changeId(), change.changedAt());
    }
}