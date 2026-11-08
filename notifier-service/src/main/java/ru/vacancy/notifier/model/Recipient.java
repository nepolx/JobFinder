package ru.vacancy.notifier.model;

import java.time.OffsetDateTime;

/**
 * Одна запись журнала notification_log, ожидающая отправки.
 * Ключ записи: (changeId, changedAt, userId).
 *
 * @param inFavorites лежит ли вакансия у пользователя в избранном: от этого зависит, получит он
 *                    уведомление «изменилась» или «новая» (для событий updated)
 */
public record Recipient(long changeId, OffsetDateTime changedAt, int userId, long telegramId, boolean inFavorites) {
}
