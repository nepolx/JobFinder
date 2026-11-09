package ru.vacancybot.bot.domain;

import java.util.List;

/** Что изменилось в избранной вакансии (приходит от Worker/Notifier). */
public record VacancyChange(Vacancy vacancy, List<FieldChange> changes, boolean closed) {

    public record FieldChange(String field, String before, String after) {
    }
}
