package ru.vacancy.notifier.limit;

/** Всё, что нужно сделать перед отправкой сообщения в чат. */
public interface ChatRateLimiter {
    void acquire(long chatId);
}
