package ru.vacancybot.bot.fsm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Контекст FSM в Redis: реплики bot-service остаются stateless. Включается bot.session-store=redis.
 * Ключ bot:session:{telegramId}, значение — JSON, TTL 6 часов с последнего действия.
 */
@Component
@ConditionalOnProperty(name = "bot.session-store", havingValue = "redis")
public class RedisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);
    private static final Duration TTL = Duration.ofHours(6);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public RedisSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Session load(long telegramId) {
        String json = redis.opsForValue().get(key(telegramId));
        if (json == null) {
            return new Session();
        }
        try {
            return mapper.readValue(json, Session.class);
        } catch (Exception e) {
            log.warn("Сессия пользователя {} повреждена и будет сброшена: {}", telegramId, e.getMessage());
            return new Session();
        }
    }

    @Override
    public void save(long telegramId, Session session) {
        if (session.getState() == DialogState.IDLE) {
            redis.delete(key(telegramId));
            return;
        }
        try {
            redis.opsForValue().set(key(telegramId), mapper.writeValueAsString(session), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot save session", e);
        }
    }

    private static String key(long telegramId) {
        return "bot:session:" + telegramId;
    }
}
