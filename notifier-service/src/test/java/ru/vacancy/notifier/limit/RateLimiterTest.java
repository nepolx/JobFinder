package ru.vacancy.notifier.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    // ---- TokenBucket ----

    @Test
    void burstUpToCapacityThenWait() {
        TokenBucket b = new TokenBucket(2, 3, 0);
        assertEquals(0.0, b.reserve(0), 1e-9);
        assertEquals(0.0, b.reserve(0), 1e-9);
        assertEquals(0.0, b.reserve(0), 1e-9);
        assertEquals(0.5, b.reserve(0), 1e-9);   // 4-й ждёт 1/rate
    }

    @Test
    void parallelReservationsQueueInOrder() {
        TokenBucket b = new TokenBucket(1, 1, 0);
        for (int i = 0; i < 4; i++) {
            assertEquals((double) i, b.reserve(0), 1e-9);
        }
    }

    @Test
    void refillsOverTimeButNotAboveCapacity() {
        TokenBucket b = new TokenBucket(1, 2, 0);
        b.reserve(0);
        b.reserve(0);
        assertEquals(0.0, b.reserve(100), 1e-9);  // накопилось, но не больше capacity
        assertEquals(0.0, b.reserve(100), 1e-9);
        assertEquals(1.0, b.reserve(100), 1e-9);
    }

    @Test
    void sustainedRateIsRespected() {
        // 100 запросов подряд при rate=10/с не могут закончиться раньше чем за ~9.5 с
        TokenBucket b = new TokenBucket(10, 1, 0);
        double last = 0;
        for (int i = 0; i < 100; i++) {
            last = b.reserve(0);
        }
        assertTrue(last >= 9.5, "last=" + last);
    }

    @Test
    void rejectsBadParameters() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucket(1, 0, 0));
    }

    // ---- InMemoryRateLimiter ----

    static final class Fake {
        double now = 0;
        final List<Double> sleeps = new ArrayList<>();

        InMemoryRateLimiter limiter(double rate, int capacity) {
            return new InMemoryRateLimiter(rate, capacity, () -> now, seconds -> {
                sleeps.add(seconds);
                now += seconds;
            });
        }
    }

    @Test
    void noSleepWithinBurst() {
        Fake f = new Fake();
        InMemoryRateLimiter l = f.limiter(1, 2);
        l.acquire("k");
        l.acquire("k");
        assertTrue(f.sleeps.isEmpty());
    }

    @Test
    void sleepsWhenExhausted() {
        Fake f = new Fake();
        InMemoryRateLimiter l = f.limiter(1, 1);
        l.acquire("k");
        l.acquire("k");
        assertEquals(List.of(1.0), f.sleeps);
    }

    @Test
    void keysAreIndependent() {
        Fake f = new Fake();
        InMemoryRateLimiter l = f.limiter(1, 1);
        l.acquire("chat:1");
        l.acquire("chat:2");
        assertTrue(f.sleeps.isEmpty());
    }

    // ---- CompositeRateLimiter ----

    @Test
    void chatLimitIsTakenBeforeGlobal() {
        List<String> calls = new ArrayList<>();
        CompositeRateLimiter c = new CompositeRateLimiter(
                key -> calls.add("chat/" + key), key -> calls.add("global/" + key));
        c.acquire(42);
        assertEquals(List.of("chat/chat:42", "global/global"), calls);
    }
}
