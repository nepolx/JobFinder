package ru.vacancybot.common.dto;

/** Вакансия в объёме, нужном для карточек и уведомлений. */
public record VacancyDto(
        String id,
        String jobName,
        String employerName,
        String regionName,
        Integer salaryMin,
        Integer salaryMax,
        String schedule,
        String url
) {}