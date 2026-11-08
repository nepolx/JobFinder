package ru.vacancybot.bot.domain;

public record Subscription(long id, SubscriptionCriteria criteria, boolean active) {
}
