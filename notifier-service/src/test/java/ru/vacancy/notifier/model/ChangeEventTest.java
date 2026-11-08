package ru.vacancy.notifier.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChangeEventTest {

    static final String VID = "3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d";

    /** Корректное событие; overrides заменяют поля, null в значении удаляет поле. */
    static Map<String, Object> raw(Object... overrides) {
        Map<String, Object> m = new HashMap<>();
        m.put("change_id", 7);
        m.put("vacancy_id", VID);
        m.put("type", "updated");
        m.put("changed_at", "2026-11-08T12:00:00Z");
        m.put("diff", Map.of("salary_min", List.of(50000, 70000)));
        for (int i = 0; i < overrides.length; i += 2) {
            if (overrides[i + 1] == null) {
                m.remove((String) overrides[i]);
            } else {
                m.put((String) overrides[i], overrides[i + 1]);
            }
        }
        return m;
    }

    @Test
    void parsesValidEvent() {
        ChangeEvent e = ChangeEvent.fromMap(raw());
        assertEquals(7L, e.changeId());
        assertEquals(UUID.fromString(VID), e.vacancyId());
        assertEquals(ChangeType.UPDATED, e.type());
        assertEquals(OffsetDateTime.of(2026, 11, 8, 12, 0, 0, 0, ZoneOffset.UTC), e.changedAt());
        assertEquals(List.of(50000, 70000), e.diff().get("salary_min"));
    }

    @Test
    void diffIsOptional() {
        assertTrue(ChangeEvent.fromMap(raw("diff", null)).diff().isEmpty());
    }

    @Test
    void acceptsChangeIdAsNumericString() {
        assertEquals(42L, ChangeEvent.fromMap(raw("change_id", "42")).changeId());
    }

    @Test
    void changedAtWithOffsetIsAccepted() {
        ChangeEvent e = ChangeEvent.fromMap(raw("changed_at", "2026-11-08T15:00:00+03:00"));
        assertEquals(OffsetDateTime.parse("2026-11-08T12:00:00Z").toInstant(), e.changedAt().toInstant());
    }

    @Test
    void changedAtIsTruncatedToMicrosecondsLikePostgres() {
        ChangeEvent e = ChangeEvent.fromMap(raw("changed_at", "2026-11-08T12:00:00.123456789Z"));
        assertEquals(123456000, e.changedAt().getNano());
    }

    @Test
    void rejectsBadInput() {
        List<Map<String, Object>> bad = List.of(
                raw("type", "deleted"),
                raw("vacancy_id", "123"),
                raw("vacancy_id", "1-1-1-1-1"),               // UUID.fromString такое бы принял
                raw("change_id", "abc"),
                raw("change_id", 1.5),
                raw("changed_at", "2026-11-08T12:00:00"),      // без часового пояса
                raw("changed_at", "вчера"),
                raw("changed_at", 1731067200),
                raw("diff", List.of(1, 2)),
                raw("diff", Map.of("salary_min", 50000)),      // не пара [было, стало]
                raw("diff", Map.of("salary_min", List.of(1, 2, 3))));
        for (Map<String, Object> body : bad) {
            assertThrows(InvalidEventException.class, () -> ChangeEvent.fromMap(body), body.toString());
        }
    }

    @Test
    void rejectsMissingFieldsAndNamesTheField() {
        for (String field : List.of("change_id", "vacancy_id", "type", "changed_at")) {
            InvalidEventException e =
                    assertThrows(InvalidEventException.class, () -> ChangeEvent.fromMap(raw(field, null)));
            assertTrue(e.getMessage().contains(field), e.getMessage());
        }
    }

    @Test
    void rejectsNullBody() {
        assertThrows(InvalidEventException.class, () -> ChangeEvent.fromMap(null));
    }
}
