package ru.vacancybot.bot.fsm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Хранилище в памяти процесса. Подходит для одной реплики и разработки. */
@Component
@ConditionalOnProperty(name = "bot.session-store", havingValue = "memory", matchIfMissing = true)
public class InMemorySessionStore implements SessionStore {

    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Session load(long telegramId) {
        return sessions.computeIfAbsent(telegramId, k -> new Session());
    }

    @Override
    public void save(long telegramId, Session session) {
        if (session.getState() == DialogState.IDLE) {
            sessions.remove(telegramId); // не копим пустые сессии
        } else {
            sessions.put(telegramId, session);
        }
    }
}
