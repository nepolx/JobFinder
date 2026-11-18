package ru.vacancy.notifier.limit;

/**
 * Лимит на чат и общий лимит. Сначала чат: долгое ожидание не должно держать
 * «забронированный» глобальный токен.
 */
public final class CompositeRateLimiter implements ChatRateLimiter {

    private final RateLimiter perChat;
    private final RateLimiter global;

    public CompositeRateLimiter(RateLimiter perChat, RateLimiter global) {
        this.perChat = perChat;
        this.global = global;
    }

    @Override
    public void acquire(long chatId) {
        perChat.acquire("chat:" + chatId);
        global.acquire("global");
    }
}
