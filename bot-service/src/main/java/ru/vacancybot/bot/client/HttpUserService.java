package ru.vacancybot.bot.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.vacancybot.bot.port.UserService;

/** POST /api/users/{telegramId}: get-or-create, повторный вызов безопасен. */
@Service
@ConditionalOnProperty(name = "bot.stubs.enabled", havingValue = "false")
public class HttpUserService implements UserService {

    private final DbServiceClient db;

    public HttpUserService(DbServiceClient db) {
        this.db = db;
    }

    @Override
    public void register(long telegramId) {
        db.call("POST", "/api/users/" + telegramId, null);
    }
}
