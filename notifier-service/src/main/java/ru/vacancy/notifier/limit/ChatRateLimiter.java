package ru.vacancy.notifier.limit;

public interface ChatRateLimiter {
    void acquire(long chatId);
}
