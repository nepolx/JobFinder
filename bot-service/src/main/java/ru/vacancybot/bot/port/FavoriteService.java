package ru.vacancybot.bot.port;

import ru.vacancybot.bot.domain.Favorite;
import ru.vacancybot.bot.domain.FavoriteStatus;

import java.util.List;
import java.util.Optional;

/** Контракт с сервисом напарника: избранное и пометки. */
public interface FavoriteService {

    /** Добавляет в избранное со статусом SAVED; повторное добавление не должно падать. */
    void add(long telegramId, String vacancyId);

    void remove(long telegramId, String vacancyId);

    List<Favorite> list(long telegramId);

    Optional<Favorite> find(long telegramId, String vacancyId);

    void setStatus(long telegramId, String vacancyId, FavoriteStatus status);
}
