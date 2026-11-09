package ru.vacancybot.worker.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrudvsemApiResponse(
        String status,
        RequestInfo request,
        Meta meta,
        Results results
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RequestInfo(
            String api
    ) {}

    /** При ошибке API вместо total и limit приходит только error. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(
            Integer total,
            Integer limit,
            String error
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Results(
            List<VacancyWrapper> vacancies
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VacancyWrapper(
            TrudvsemVacancyDto vacancy
    ) {}
}