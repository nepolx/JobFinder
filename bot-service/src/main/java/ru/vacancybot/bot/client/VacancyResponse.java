package ru.vacancybot.bot.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import ru.vacancybot.bot.domain.Vacancy;

/** Вакансия из GET /api/vacancies (поля как у VacancyDto в common). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VacancyResponse(
        String id,
        String jobName,
        String employerName,
        String regionName,
        Integer salaryMin,
        Integer salaryMax,
        String schedule,
        String url) {

    public Vacancy toDomain() {
        return new Vacancy(id, jobName, employerName, regionName, salaryMin, salaryMax, schedule, url);
    }
}
