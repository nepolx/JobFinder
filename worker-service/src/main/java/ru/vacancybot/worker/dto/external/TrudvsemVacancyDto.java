package ru.vacancybot.worker.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TrudvsemVacancyDto(
        String id,

        TrudvsemRegionDto region,

        TrudvsemCompanyDto company,

        @JsonProperty("job-name")
        String jobName,

        @JsonProperty("salary_min")
        Integer salaryMin,

        @JsonProperty("salary_max")
        Integer salaryMax,

        String schedule,

        String duty,

        TrudvsemRequirementDto requirement,

        String requirements,

        @JsonProperty("vac_url")
        String vacUrl,

        @JsonProperty("date_modify")
        String dateModify
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrudvsemRegionDto(
            @JsonProperty("region_code")
            String regionCode,
            String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrudvsemCompanyDto(
            String name
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrudvsemRequirementDto(
            Integer experience
    ) {}
}