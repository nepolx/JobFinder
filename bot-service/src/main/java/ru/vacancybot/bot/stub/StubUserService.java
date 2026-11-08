package ru.vacancybot.bot.stub;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.vacancybot.bot.port.UserService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Заглушка: хранит пользователей в памяти. Заменяется реальным сервисом напарника. */
@Service
@ConditionalOnProperty(name = "bot.stubs.enabled", havingValue = "true", matchIfMissing = true)
public class StubUserService implements UserService {

    private final Map<Long, Long> ids = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    @Override
    public long register(long telegramId) {
        return ids.computeIfAbsent(telegramId, k -> seq.incrementAndGet());
    }
}
