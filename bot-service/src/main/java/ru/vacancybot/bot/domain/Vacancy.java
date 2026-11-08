package ru.vacancybot.bot.domain;

/** Вакансия в объёме, нужном боту для карточек и уведомлений (подмножество таблицы vacancies). */
public record Vacancy(
        String id,
        String jobName,
        String employerName,
        String regionName,
        Integer salaryMin,
        Integer salaryMax,
        String schedule,
        String url) {
}
