package ru.vacancy.notifier.limit;

public interface RateLimiter {
    void acquire(String key);
}
