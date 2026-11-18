package ru.vacancybot.bot.port;

/**
 * Контракт с сервисом напарника: пользователи.
 * Реализация обязана быть идемпотентной (get-or-create по telegram_id).
 */
public interface UserService {

    void register(long telegramId);
}
