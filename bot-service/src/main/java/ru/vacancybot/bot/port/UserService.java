package ru.vacancybot.bot.port;

/**
 * Контракт с сервисом напарника: пользователи.
 * Реализация обязана быть идемпотентной (get-or-create по telegram_id).
 */
public interface UserService {

    /** @return внутренний users.id */
    long register(long telegramId);
}
