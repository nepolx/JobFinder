package ru.vacancybot.common.event;

import java.util.List;

/** Событие «избранная вакансия изменилась / закрыта» (Worker -> Notifier). */
public record VacancyChangedEvent(
        long chatId,
        String vacancyId,
        String title,
        String employer,
        List<FieldChange> changes,
        boolean closed,
        String url
) {}
