package ru.vacancy.notifier.retry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.junit.jupiter.api.Test;

import ru.vacancy.notifier.telegram.PermanentException;
import ru.vacancy.notifier.telegram.RetryAfterException;
import ru.vacancy.notifier.telegram.TemporaryException;

class RetryExecutorTest {

    final List<Double> sleeps = new ArrayList<>();
    int calls = 0;

    RetryExecutor executor(int maxAttempts) {
        return new RetryExecutor(maxAttempts, 1.0, 30.0, sleeps::add, d -> d);
    }

    /** Действие, которое по очереди бросает errors, а потом выполняется успешно. */
    Runnable flaky(RuntimeException... errors) {
        Queue<RuntimeException> queue = new LinkedList<>(List.of(errors));
        return () -> {
            calls++;
            if (!queue.isEmpty()) {
                throw queue.poll();
            }
        };
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        double[] expected = {1, 2, 4, 8, 10, 10};
        for (int attempt = 1; attempt <= 6; attempt++) {
            assertEquals(expected[attempt - 1], RetryExecutor.computeBackoff(attempt, 1, 10), 1e-9);
        }
    }

    @Test
    void successOnFirstTryDoesNotSleep() {
        executor(5).run(flaky());
        assertEquals(1, calls);
        assertEquals(List.of(), sleeps);
    }

    @Test
    void retriesTemporaryErrorsWithBackoff() {
        executor(5).run(flaky(new TemporaryException("a"), new TemporaryException("b")));
        assertEquals(3, calls);
        assertEquals(List.of(1.0, 2.0), sleeps);
    }

    @Test
    void honorsRetryAfterFromTelegram() {
        executor(5).run(flaky(new RetryAfterException(7)));
        assertEquals(List.of(7.0), sleeps);
    }

    @Test
    void permanentErrorIsNotRetried() {
        assertThrows(PermanentException.class, () -> executor(5).run(flaky(new PermanentException("bad"))));
        assertEquals(1, calls);
        assertEquals(List.of(), sleeps);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        RuntimeException[] errors = new RuntimeException[10];
        for (int i = 0; i < errors.length; i++) {
            errors[i] = new TemporaryException("e" + i);
        }
        assertThrows(TemporaryException.class, () -> executor(3).run(flaky(errors)));
        assertEquals(3, calls);
        assertEquals(2, sleeps.size());   // после последней попытки не спим
    }

    @Test
    void unexpectedExceptionsPropagateImmediately() {
        assertThrows(IllegalStateException.class, () -> executor(5).run(flaky(new IllegalStateException("bug"))));
        assertEquals(1, calls);
    }
}
