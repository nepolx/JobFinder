package ru.vacancybot.common.dto;

/** Избранная вакансия: status = SAVED / APPLIED / WAITING ..., note — заметка. */
public record FavoriteDto(
        Long id,
        Long telegramUserId,
        String vacancyId,
        String status,
        String note
) {}
