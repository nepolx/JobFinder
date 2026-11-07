package ru.vacancybot.common.dto;

/** Подписка на поиск. Незаполненные (пропущенные) параметры = null. */
public record SubscriptionDto(
        Long id,
        Long telegramUserId,
        String title,
        String region,
        Integer salaryMin,
        String experience,
        String schedule,
        String employment,
        String keywords,
        boolean active
) {}
