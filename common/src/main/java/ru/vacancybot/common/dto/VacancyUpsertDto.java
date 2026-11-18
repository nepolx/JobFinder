package ru.vacancybot.common.dto;

import java.time.Instant;

/**
 * Вакансия, которую worker отправляет в db-service (POST /api/vacancies/sync).
 * Обязательные поля: id, employerName, regionCode, regionName, jobName, url, modifiedAt.
 * Зарплата 0 превращается в null на стороне db-service.
 */
public record VacancyUpsertDto(
        String id,
        String employerName,
        String regionCode,
        String regionName,
        String jobName,
        Integer salaryMin,
        Integer salaryMax,
        String schedule,
        String scheduleCode,
        String employmentCode,
        Integer experienceYears,
        String duty,
        String requirements,
        String url,
        Instant modifiedAt
) {}