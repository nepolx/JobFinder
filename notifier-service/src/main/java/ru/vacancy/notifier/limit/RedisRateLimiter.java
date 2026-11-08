package ru.vacancy.notifier.limit;

import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import ru.vacancy.notifier.util.Sleeper;

/**
 * Общий для всех копий Notifier лимитер: та же математика, что в {@link TokenBucket},
 * но выполняется атомарно внутри Redis (Lua), время берётся с часов Redis.
 * Ключи: {prefix}:global и {prefix}:chat:{id}. Bot API может брать токены из того же
 * {prefix}:global, чтобы его ответы учитывались в общем лимите Telegram.
 */
public final class RedisRateLimiter implements RateLimiter {

    private static final String LUA = """
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local t = redis.call('TIME')
            local now = t[1] * 1000 + math.floor(t[2] / 1000)
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(state[1])
            local ts = tonumber(state[2])
            if tokens == nil then
              tokens = capacity
              ts = now
            end
            tokens = math.min(capacity, tokens + math.max(0, now - ts) * rate / 1000)
            tokens = tokens - 1
            local wait_ms = 0
            if tokens < 0 then
              wait_ms = math.ceil(-tokens * 1000 / rate)
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
            redis.call('PEXPIRE', KEYS[1], math.ceil(capacity * 1000 / rate) + wait_ms + 1000)
            return wait_ms
            """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    private final StringRedisTemplate redis;
    private final double rate;
    private final int capacity;
    private final String prefix;
    private final Sleeper sleeper;

    public RedisRateLimiter(StringRedisTemplate redis, double rate, int capacity, String prefix, Sleeper sleeper) {
        this.redis = redis;
        this.rate = rate;
        this.capacity = capacity;
        this.prefix = prefix;
        this.sleeper = sleeper;
    }

    @Override
    public void acquire(String key) {
        Long waitMs = redis.execute(
                SCRIPT, List.of(prefix + ":" + key), Double.toString(rate), Integer.toString(capacity));
        if (waitMs != null && waitMs > 0) {
            sleeper.sleep(waitMs / 1000.0);
        }
    }
}
