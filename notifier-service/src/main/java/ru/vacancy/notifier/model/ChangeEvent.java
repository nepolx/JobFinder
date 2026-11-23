package ru.vacancy.notifier.model;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public record ChangeEvent(
        long changeId,
        UUID vacancyId,
        ChangeType type,
        OffsetDateTime changedAt,
        Map<String, List<Object>> diff) {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public ChangeEvent asNewVacancy() {
        return new ChangeEvent(changeId, vacancyId, ChangeType.CREATED, changedAt, Map.of());
    }

    
    public static ChangeEvent fromMap(Map<String, ?> data) {
        if (data == null) {
            throw new InvalidEventException("событие должно быть JSON-объектом");
        }
        long changeId = toLong(require(data, "change_id"));
        UUID vacancyId = toUuid(require(data, "vacancy_id"));
        ChangeType type = toType(require(data, "type"));
        OffsetDateTime changedAt = toTimestamp(require(data, "changed_at"));
        return new ChangeEvent(changeId, vacancyId, type, changedAt, toDiff(data.get("diff")));
    }

    private static Object require(Map<String, ?> data, String name) {
        Object value = data.get(name);
        if (value == null) {
            throw new InvalidEventException("нет поля '" + name + "'");
        }
        return value;
    }

    private static long toLong(Object raw) {
        if (raw instanceof Number n) {
            if (n.doubleValue() != Math.rint(n.doubleValue())) {
                throw new InvalidEventException("change_id должен быть целым: " + raw);
            }
            return n.longValue();
        }
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                throw new InvalidEventException("change_id не число: " + s);
            }
        }
        throw new InvalidEventException("change_id имеет неверный тип");
    }

    private static UUID toUuid(Object raw) {
        
        if (raw instanceof String s && UUID_FORMAT.matcher(s).matches()) {
            return UUID.fromString(s);
        }
        throw new InvalidEventException("vacancy_id не похож на UUID: " + raw);
    }

    private static ChangeType toType(Object raw) {
        try {
            return ChangeType.fromValue(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException(e.getMessage());
        }
    }

    private static OffsetDateTime toTimestamp(Object raw) {
        if (!(raw instanceof String s)) {
            throw new InvalidEventException("changed_at должно быть строкой ISO-8601");
        }
        try {
            return OffsetDateTime.parse(s).truncatedTo(ChronoUnit.MICROS);
        } catch (DateTimeParseException e) {
            throw new InvalidEventException("changed_at не ISO-8601 с часовым поясом: " + s);
        }
    }

    private static Map<String, List<Object>> toDiff(Object raw) {
        if (raw == null) {
            return Map.of();
        }
        if (!(raw instanceof Map<?, ?> map)) {
            throw new InvalidEventException("diff должен быть объектом {поле: [было, стало]}");
        }
        Map<String, List<Object>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!(e.getValue() instanceof List<?> pair) || pair.size() != 2) {
                throw new InvalidEventException("diff должен иметь вид {поле: [было, стало]}");
            }
            result.put(String.valueOf(e.getKey()), Collections.unmodifiableList(new ArrayList<Object>(pair)));
        }
        return Collections.unmodifiableMap(result);
    }
}
