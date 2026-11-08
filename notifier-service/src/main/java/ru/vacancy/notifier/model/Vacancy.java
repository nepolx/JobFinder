package ru.vacancy.notifier.model;

import java.util.UUID;

public record Vacancy(
        UUID id,
        String jobName,
        String employerName,
        String regionName,
        Integer salaryMin,
        Integer salaryMax,
        String schedule,
        String vacUrl) {
}
