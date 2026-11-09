package ru.vacancybot.bot.stub;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.vacancybot.bot.port.UserService;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Заглушка: хранит пользователей в памяти. Заменяется реальным сервисом напарника. */
@Service
@ConditionalOnProperty(name = "bot.stubs.enabled", havingValue = "true", matchIfMissing = true)
public class StubUserService implements UserService {

    private final Set<Long> registered = ConcurrentHashMap.newKeySet();

    @Override
    public void register(long telegramId) {
        registered.add(telegramId);
    }

    public boolean isRegistered(long telegramId) {
        return registered.contains(telegramId);
    }
}
