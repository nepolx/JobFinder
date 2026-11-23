package ru.vacancy.notifier.limit;

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
