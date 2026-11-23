package ru.vacancybot.worker.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertiesValidationTest {

    private static SyncProperties sync(Duration window, int reloads, int retries, Duration retryDelay,
                                       Duration pause, int maxWindowRecords, int concurrency) {
        return new SyncProperties(window, reloads, retries, retryDelay, pause, maxWindowRecords, concurrency);
    }

    private static SyncProperties validSync() {
        return sync(Duration.ofHours(1), 2, 3, Duration.ofSeconds(1), Duration.ofMillis(100), 10_000, 3);
    }

    @Test
    void validSyncSettingsAreAccepted() {
        assertThatCode(PropertiesValidationTest::validSync).doesNotThrowAnyException();
        assertThat(validSync().pageConcurrency()).isEqualTo(3);
    }

    @Test
    void windowShorterThanOneSecondIsRejected() {
        assertThatThrownBy(() -> sync(Duration.ofMillis(500), 2, 3, Duration.ofSeconds(1), Duration.ZERO, 10_000, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 9, 100})
    void pageConcurrencyOutsideOneToEightIsRejected(int concurrency) {
        assertThatThrownBy(() -> sync(Duration.ofHours(1), 2, 3, Duration.ofSeconds(1), Duration.ZERO, 10_000, concurrency))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8})
    void boundaryPageConcurrencyIsAccepted(int concurrency) {
        assertThatCode(() -> sync(Duration.ofHours(1), 2, 3, Duration.ofSeconds(1), Duration.ZERO, 10_000, concurrency))
                .doesNotThrowAnyException();
    }

    @Test
    void nonPositiveWindowRecordLimitIsRejected() {
        assertThatThrownBy(() -> sync(Duration.ofHours(1), 2, 3, Duration.ofSeconds(1), Duration.ZERO, 0, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeCountersAndDelaysAreRejected() {
        assertThatThrownBy(() -> sync(Duration.ofHours(1), -1, 3, Duration.ofSeconds(1), Duration.ZERO, 10_000, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sync(Duration.ofHours(1), 2, -1, Duration.ofSeconds(1), Duration.ZERO, 10_000, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sync(Duration.ofHours(1), 2, 3, Duration.ofSeconds(-1), Duration.ZERO, 10_000, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sync(Duration.ofHours(1), 2, 3, Duration.ofSeconds(1), Duration.ofMillis(-1), 10_000, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroRetriesAndReloadsAreAllowed() {
        assertThatCode(() -> sync(Duration.ofHours(1), 0, 0, Duration.ZERO, Duration.ZERO, 10_000, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void validRunSettingsAreAccepted() {
        assertThatCode(() -> new RunProperties(Duration.ofHours(2), Duration.ofHours(24), Duration.ofHours(48)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new RunProperties(Duration.ZERO, Duration.ofHours(1), Duration.ofHours(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidRunSettingsAreRejected() {
        assertThatThrownBy(() -> new RunProperties(Duration.ofHours(-1), Duration.ofHours(24), Duration.ofHours(48)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RunProperties(Duration.ofHours(2), Duration.ZERO, Duration.ofHours(48)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RunProperties(Duration.ofHours(2), Duration.ofHours(24), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validEventsSettingsAreAccepted() {
        assertThatCode(() -> new EventsProperties("vacancy.events", 200, Duration.ofSeconds(10)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new EventsProperties("vacancy.events", 1000, Duration.ofMillis(1)))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidEventsSettingsAreRejected() {
        assertThatThrownBy(() -> new EventsProperties(" ", 200, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventsProperties(null, 200, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventsProperties("q", 0, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventsProperties("q", 1001, Duration.ofSeconds(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EventsProperties("q", 200, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
