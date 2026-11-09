package ru.vacancybot.db.service;

import org.springframework.stereotype.Component;
import ru.vacancybot.db.entity.Vacancy;

import java.util.*;

/**
 * Сравнить поля old и incoming и вернуть только изменившиеся: {имя колонки: [было, стало]}.
 * Поля для сравнения и ключи (имена колонок таблицы vacancies):
 *   job_name, employer_name, salary_min, salary_max, schedule, schedule_code,
 *   employment_code, experience_years, duty, requirements.
 * Не сравниваем: id, region_code, region_name, vac_url, modified_at.
 * null и «значение» считаются разными (например, {"salary_max": [null, 90000]}).
 * Если различий нет, вернуть пустую карту (тогда событие не создаётся).
 */
@Component
public class VacancyComparatorImpl implements VacancyComparator {

    @Override
    public Map<String, List<Object>> diff(Vacancy old, Vacancy incoming) {
        Map<String, List<Object>> changes = new LinkedHashMap<>();
        addIfChanged(changes, "job_name", old.getJobName(), incoming.getJobName());
        addIfChanged(changes, "salary_min", old.getSalaryMin(), incoming.getSalaryMin());
        addIfChanged(changes, "salary_max", old.getSalaryMax(), incoming.getSalaryMax());
        addIfChanged(changes, "employer_name", old.getEmployerName(), incoming.getEmployerName());
        addIfChanged(changes, "schedule", old.getSchedule(), incoming.getSchedule());
        addIfChanged(changes, "schedule_code", old.getScheduleCode(), incoming.getScheduleCode());
        addIfChanged(changes, "employment_code", old.getEmploymentCode(), incoming.getEmploymentCode());
        addIfChanged(changes, "experience_years", old.getExperienceYears(), incoming.getExperienceYears());
        addIfChanged(changes, "duty", old.getDuty(), incoming.getDuty());
        addIfChanged(changes, "requirements", old.getRequirements(), incoming.getRequirements());
        return changes;
    }

    private static void addIfChanged(Map<String, List<Object>> changes,
                                     String column, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.put(column, Arrays.asList(before, after));
        }
    }
}