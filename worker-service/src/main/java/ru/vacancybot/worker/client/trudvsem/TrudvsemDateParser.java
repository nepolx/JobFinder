package ru.vacancybot.worker.client.trudvsem;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Разбор дат из ответа trudvsem.ru.
 *
 * <p>API отдаёт {@code date_modify} в виде {@code 2026-11-09T03:59:13+0300}: смещение
 * без двоеточия, поэтому стандартный {@code ISO_OFFSET_DATE_TIME} не подходит.
 * Шаблон {@code XX} принимает и {@code +0300}, и {@code Z}.
 */
public final class TrudvsemDateParser {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXX");

    private TrudvsemDateParser() {
    }

    /**
     * @throws IllegalArgumentException если значение пустое или не соответствует формату
     */
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