package ru.vacancybot.worker.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import ru.vacancybot.worker.dto.dbservice.VacancyChangeDto;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record VacancyEvent(
        @JsonProperty("change_id") long changeId,
        @JsonProperty("changed_at") String changedAt,
        @JsonProperty("vacancy_id") String vacancyId,
        @JsonProperty("type") String type,
        @JsonProperty("diff") Map<String, List<Object>> diff
) {

    public static VacancyEvent from(VacancyChangeDto change) {
        if (change.changedAt() == null) {
            throw new IllegalArgumentException("changedAt is missing: notifier would drop the event");
        }
        if (change.vacancyId() == null || change.vacancyId().isBlank()) {
            throw new IllegalArgumentException("vacancyId is missing");
        }
        String type = change.changeType() == null ? "" : change.changeType().trim().toLowerCase(Locale.ROOT);
        if (!type.equals("created") && !type.equals("updated")) {
            throw new IllegalArgumentException("Unsupported change type: " + change.changeType());
        }

        Map<String, List<Object>> diff = null;
        if (type.equals("updated")) {
            diff = change.changes() == null ? Map.of() : change.changes();
        }
        return new VacancyEvent(
                change.changeId(),
                DateTimeFormatter.ISO_INSTANT.format(change.changedAt()),
                change.vacancyId(),
                type,
                diff);
    }
}