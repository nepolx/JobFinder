package ru.vacancybot.bot.fsm;

/**
 * Хранилище сессий. Bot API по схеме масштабирования stateless (3–4 реплики за nginx),
 * поэтому в продакшене реализация должна быть общей для реплик (Redis, TTL в несколько часов).
 */
public interface SessionStore {

    /** Возвращает сессию пользователя; если её нет — новую (IDLE). */
    Session load(long telegramId);

    void save(long telegramId, Session session);
}
