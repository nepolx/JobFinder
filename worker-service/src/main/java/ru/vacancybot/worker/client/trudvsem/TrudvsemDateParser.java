package ru.vacancybot.worker.client.trudvsem;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class TrudvsemDateParser {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX");

    private TrudvsemDateParser() {
    }

    public static Instant parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("date_modify is empty");
        }
        try {
            return OffsetDateTime.parse(value.trim(), FORMAT).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Unexpected date format: " + value, e);
        }
    }
}