package ru.vacancybot.worker.dto.dbservice;

import java.time.Instant;

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