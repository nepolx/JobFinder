package ru.vacancybot.db.repository;

/** Строка результата подбора подписчиков: кому отправить и по какой подписке сработало. */
public interface SubscriberMatch {

    Long getTelegramId();

    Integer getSubscriptionId();
}