package ru.vacancybot.db.service;

import org.springframework.stereotype.Component;
import ru.vacancybot.db.entity.Vacancy;

import java.util.List;
import java.util.Map;

/**
 * TODO (Маша): реализовать сравнение.
 *
 * Сравнить поля old и incoming и вернуть только изменившиеся: {имя колонки: [было, стало]}.
 * Поля для сравнения и ключи (имена колонок таблицы vacancies):
 *   job_name, employer_name, salary_min, salary_max, schedule, schedule_code,
 *   employment_code, experience_years, duty, requirements.
 * Не сравниваем: id, region_code, region_name, vac_url, modified_at.
 * null и «значение» считаются разными (например, {"salary_max": [null, 90000]}).
 * Если различий нет, вернуть пустую карту (тогда событие не создаётся).
 *
 * Пока метод не реализован, POST /api/vacancies/sync для уже известных вакансий отвечает ошибкой 500.
 */
@Component
public class VacancyComparatorImpl implements VacancyComparator {

    @Override
    public Map<String, List<Object>> diff(Vacancy old, Vacancy incoming) {
        throw new UnsupportedOperationException("Сравнение вакансий ещё не реализовано (Маша)");
    }
}