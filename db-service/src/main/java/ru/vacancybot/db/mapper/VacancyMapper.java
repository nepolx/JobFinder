package ru.vacancybot.db.mapper;

import ru.vacancybot.common.dto.VacancyDto;
import ru.vacancybot.db.entity.Vacancy;

public final class VacancyMapper {

    private VacancyMapper() {
    }

    public static VacancyDto toDto(Vacancy v) {
        return new VacancyDto(
                v.getId().toString(),
                v.getJobName(),
                v.getEmployerName(),
                v.getRegionName(),
                v.getSalaryMin(),
                v.getSalaryMax(),
                v.getSchedule(),
                v.getVacUrl());
    }
}