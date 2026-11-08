package ru.vacancy.notifier.limit;

/**
 * Token bucket с «бронированием»: вызывающий всегда списывает токен, а если токенов не
 * хватило, получает время ожидания. Параллельные вызовы встают в очередь по порядку и не
 * опрашивают счётчик в цикле. Чистая логика без часов и ввода-вывода.
 */
public final class TokenBucket {

    private final double rate;
    private final int capacity;
    private double tokens;
    private double updated;

    public TokenBucket(double rate, int capacity, double now) {
        if (rate <= 0 || capacity < 1) {
            throw new IllegalArgumentException("rate должен быть > 0, capacity >= 1");
        }
        this.rate = rate;
        this.capacity = capacity;
        this.tokens = capacity;
        this.updated = now;
    }

    /** Списывает токен и возвращает, сколько секунд надо подождать перед отправкой. */
    public synchronized double reserve(double now) {
        double elapsed = Math.max(0.0, now - updated);
        tokens = Math.min(capacity, tokens + elapsed * rate);
        updated = now;
        tokens -= 1;
        return tokens >= 0 ? 0.0 : -tokens / rate;
    }
}
