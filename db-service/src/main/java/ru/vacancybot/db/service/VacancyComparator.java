package ru.vacancybot.db.service;

import ru.vacancybot.db.entity.Vacancy;

import java.util.List;
import java.util.Map;

public interface VacancyComparator {

    /**
     * @param old      версия из базы (текущее состояние)
     * @param incoming версия, пришедшая от worker (в базу ещё не записана)
     * @return изменившиеся поля в виде {имя колонки vacancies: [было, стало]},
     *         например {"salary_min": [50000, 70000]}; пустая карта, если различий нет
     */
    Map<String, List<Object>> diff(Vacancy old, Vacancy incoming);
}