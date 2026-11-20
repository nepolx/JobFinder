package ru.vacancybot.worker.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            @JsonDeserialize(using = ExperienceDeserializer.class)
            Integer experience
    ) {}

    public static class ExperienceDeserializer extends StdDeserializer<Integer> {

        private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+");

        public ExperienceDeserializer() {
            super(Integer.class);
        }

        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {

            if (parser.currentToken().isNumeric()) {
                return parser.getIntValue();
            }

            String value = parser.getValueAsString();

            if (value == null || value.isBlank()) {
                return null;
            }

            Matcher matcher = NUMBER_PATTERN.matcher(value);

            if (matcher.find()) {
                try {
                    return Integer.valueOf(matcher.group());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }

            return null;
        }
    }
}