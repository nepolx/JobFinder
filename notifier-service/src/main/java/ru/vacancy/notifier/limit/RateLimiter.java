package ru.vacancy.notifier.limit;

/** Ограничитель по ключу: блокирует поток, пока лимит не позволит продолжить. */
public interface RateLimiter {
    void acquire(String key);
}
