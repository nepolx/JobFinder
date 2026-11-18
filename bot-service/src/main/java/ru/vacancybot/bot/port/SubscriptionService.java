package ru.vacancybot.bot.port;

import ru.vacancybot.bot.domain.Subscription;
import ru.vacancybot.bot.domain.SubscriptionCriteria;

import java.util.List;
import java.util.Optional;

/**
 * Контракт с сервисом напарника: подписки.
 * Все методы принимают telegramId и сами создают пользователя при необходимости и проверяют владельца подписки.
 */
public interface SubscriptionService {

    Subscription create(long telegramId, SubscriptionCriteria criteria);

    /** Заменяет критерии существующей подписки. */
    Subscription update(long telegramId, long subscriptionId, SubscriptionCriteria criteria);

    /** Все подписки пользователя (и активные, и на паузе), без удалённых. */
    List<Subscription> list(long telegramId);

    Optional<Subscription> find(long telegramId, long subscriptionId);

    /** Пауза (false) и возобновление (true). */
    void setActive(long telegramId, long subscriptionId, boolean active);

    /** Удаление подписки пользователем. */
    void delete(long telegramId, long subscriptionId);
}
