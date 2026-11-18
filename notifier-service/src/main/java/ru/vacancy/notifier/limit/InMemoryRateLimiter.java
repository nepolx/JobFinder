package ru.vacancy.notifier.limit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

import ru.vacancy.notifier.util.Sleeper;

/** Для тестов и локального запуска одной копии (между копиями счётчик не общий). */
public final class InMemoryRateLimiter implements RateLimiter {

    private final double rate;
    private final int capacity;
    private final DoubleSupplier clock;   // время в секундах
    private final Sleeper sleeper;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(double rate, int capacity, DoubleSupplier clock, Sleeper sleeper) {
        this.rate = rate;
        this.capacity = capacity;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Override
    public void acquire(String key) {
        double now = clock.getAsDouble();
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(rate, capacity, now));
        double wait = bucket.reserve(now);
        if (wait > 0) {
            sleeper.sleep(wait);
        }
    }
}
