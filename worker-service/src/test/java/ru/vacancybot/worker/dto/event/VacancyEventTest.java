package ru.vacancybot.worker.dto.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.vacancybot.worker.dto.dbservice.VacancyChangeDto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VacancyEventTest {

    private static final String VACANCY_ID = "3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d";
    private static final Instant CHANGED_AT = Instant.parse("2026-11-08T12:30:00Z");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void updatedEventHasSnakeCaseFieldsAndDiff() throws Exception {
        VacancyChangeDto change = new VacancyChangeDto(
                123L, CHANGED_AT, VACANCY_ID, "UPDATED",
                Map.of("salary_min", List.<Object>of(50000, 70000)));

        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(VacancyEvent.from(change)));

        assertThat(json.get("change_id").asLong()).isEqualTo(123);
        assertThat(json.get("changed_at").asText()).isEqualTo("2026-11-08T12:30:00Z");
        assertThat(json.get("vacancy_id").asText()).isEqualTo(VACANCY_ID);
        assertThat(json.get("type").asText()).isEqualTo("updated");
        assertThat(json.get("diff").get("salary_min").get(0).asInt()).isEqualTo(50000);
        assertThat(json.get("diff").get("salary_min").get(1).asInt()).isEqualTo(70000);
    }

    @Test
    void createdEventHasNoDiffField() throws Exception {
        VacancyChangeDto change = new VacancyChangeDto(124L, CHANGED_AT, VACANCY_ID, "created", null);

        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(VacancyEvent.from(change)));

        assertThat(json.get("type").asText()).isEqualTo("created");
        assertThat(json.has("diff")).isFalse();
    }

    @Test
    void changedAtKeepsMicrosecondPrecision() {
        Instant withMicros = Instant.parse("2026-11-08T12:30:00.123456Z");
        VacancyChangeDto change = new VacancyChangeDto(125L, withMicros, VACANCY_ID, "created", null);

        assertThat(VacancyEvent.from(change).changedAt()).isEqualTo("2026-11-08T12:30:00.123456Z");
    }

    @Test
    void rejectsChangeWithoutChangedAt() {
        VacancyChangeDto change = new VacancyChangeDto(126L, null, VACANCY_ID, "created", null);

        assertThatThrownBy(() -> VacancyEvent.from(change)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedType() {
        VacancyChangeDto change = new VacancyChangeDto(127L, CHANGED_AT, VACANCY_ID, "closed", null);

        assertThatThrownBy(() -> VacancyEvent.from(change)).isInstanceOf(IllegalArgumentException.class);
    }
}
