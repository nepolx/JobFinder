package ru.vacancy.notifier.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;

class TelegramResponsesTest {

    @Test
    void okResponseDoesNothing() {
        TelegramResponses.check(200, Map.of("ok", true, "result", Map.of()));
    }

    @Test
    void status429CarriesRetryAfter() {
        RetryAfterException e = assertThrows(RetryAfterException.class, () ->
                TelegramResponses.check(429, Map.of("ok", false, "parameters", Map.of("retry_after", 12))));
        assertEquals(12.0, e.retryAfter(), 1e-9);
    }

    @Test
    void status429WithoutParametersDefaultsToOneSecond() {
        RetryAfterException e = assertThrows(RetryAfterException.class, () -> TelegramResponses.check(429, Map.of()));
        assertEquals(1.0, e.retryAfter(), 1e-9);
    }

    @Test
    void status403MeansBlocked() {
        assertThrows(BotBlockedException.class, () ->
                TelegramResponses.check(403, Map.of("description", "Forbidden: bot was blocked by the user")));
    }

    @Test
    void chatNotFoundMeansBlocked() {
        assertThrows(BotBlockedException.class, () ->
                TelegramResponses.check(400, Map.of("description", "Bad Request: chat not found")));
    }

    @Test
    void other400IsPermanentButNotBlocked() {
        PermanentException e = assertThrows(PermanentException.class, () ->
                TelegramResponses.check(400, Map.of("description", "Bad Request: can't parse entities")));
        assertFalse(e instanceof BotBlockedException);
    }

    @Test
    void serverErrorsAreTemporary() {
        for (int status : new int[] {500, 502, 503}) {
            assertThrows(TemporaryException.class, () -> TelegramResponses.check(status, Map.of()));
        }
    }

    @Test
    void badTokenIsTemporaryNotPerUserFailure() {
        assertThrows(TemporaryException.class, () ->
                TelegramResponses.check(401, Map.of("description", "Unauthorized")));
    }

    @Test
    void nullBodyIsTreatedAsEmpty() {
        assertThrows(TemporaryException.class, () -> TelegramResponses.check(502, null));
    }

    @Test
    void status200WithoutOkFlagIsAnError() {
        assertThrows(PermanentException.class, () -> TelegramResponses.check(200, Map.of()));
    }
}
