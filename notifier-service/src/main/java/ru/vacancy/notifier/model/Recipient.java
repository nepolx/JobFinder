package ru.vacancy.notifier.model;

import java.time.OffsetDateTime;

/**
 * Одна запись журнала notification_log, ожидающая отправки.
 * Ключ записи: (changeId, changedAt, userId).
 */
public record Recipient(long changeId, OffsetDateTime changedAt, int userId, long telegramId) {
}
