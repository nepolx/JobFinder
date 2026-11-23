package ru.vacancybot.worker.client.trudvsem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrudvsemDateParserTest {

    @ParameterizedTest
    @CsvSource({
            "2026-11-09T03:59:13+0300, 2026-11-09T00:59:13Z",
            "2026-11-09T00:59:13Z,     2026-11-09T00:59:13Z",
            "2026-11-09T00:59:13-0500, 2026-11-09T05:59:13Z",
            "2026-01-01T02:00:00+0300, 2025-12-31T23:00:00Z"
    })
    void parsesOffsetWithoutColonAndConvertsToUtc(String value, String expected) {
        assertThat(TrudvsemDateParser.parse(value)).isEqualTo(Instant.parse(expected));
    }

    @Test
    void ignoresSurroundingWhitespace() {
        assertThat(TrudvsemDateParser.parse("  2026-11-09T03:59:13+0300 "))
                .isEqualTo(Instant.parse("2026-11-09T00:59:13Z"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "09.11.2026", "2026-11-09", "2026-11-09T03:59:13", "not a date"})
    void rejectsMissingOrUnexpectedFormat(String value) {
        assertThatThrownBy(() -> TrudvsemDateParser.parse(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
