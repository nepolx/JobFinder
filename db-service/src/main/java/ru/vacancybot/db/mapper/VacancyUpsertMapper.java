package ru.vacancybot.db.mapper;

import ru.vacancybot.common.dto.VacancyUpsertDto;
import ru.vacancybot.db.entity.Vacancy;

import java.util.UUID;

/** Преобразование VacancyUpsertDto (пришло от worker) в сущность Vacancy. */
public final class VacancyUpsertMapper {

    private VacancyUpsertMapper() {
    }

    /** Создаёт новый (несохранённый) объект Vacancy из DTO. */
    public static Vacancy toEntity(VacancyUpsertDto dto) {
        Vacancy vacancy = new Vacancy(parseId(dto.id()));
        applyTo(dto, vacancy);
        return vacancy;
    }

    public static void applyTo(VacancyUpsertDto dto, Vacancy vacancy) {
        vacancy.setEmployerName(required(dto.employerName(), "employerName"));
        vacancy.setRegionCode(required(dto.regionCode(), "regionCode"));
        vacancy.setRegionName(required(dto.regionName(), "regionName"));
        vacancy.setJobName(required(dto.jobName(), "jobName"));
        vacancy.setVacUrl(required(dto.url(), "url"));
        if (dto.modifiedAt() == null) {
            throw new IllegalArgumentException("modifiedAt обязателен");
        }
        vacancy.setModifiedAt(dto.modifiedAt());

        // зарплата 0 из API означает «не указана»
        vacancy.setSalaryMin(zeroToNull(dto.salaryMin()));
        vacancy.setSalaryMax(zeroToNull(dto.salaryMax()));

        vacancy.setSchedule(blankToNull(dto.schedule()));
        vacancy.setScheduleCode(blankToNull(dto.scheduleCode()));
        vacancy.setEmploymentCode(blankToNull(dto.employmentCode()));
        vacancy.setExperienceYears(dto.experienceYears());
        vacancy.setDuty(blankToNull(dto.duty()));
        vacancy.setRequirements(blankToNull(dto.requirements()));
    }

    public static UUID parseId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id вакансии обязателен");
        }
        try {
            return UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("id вакансии должен быть UUID: " + id);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " обязателен");
        }
        return value.trim();
    }

    private static Integer zeroToNull(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}