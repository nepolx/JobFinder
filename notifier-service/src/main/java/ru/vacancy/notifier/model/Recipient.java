package ru.vacancy.notifier.model;

import java.time.OffsetDateTime;

public record Recipient(long changeId, OffsetDateTime changedAt, int userId, long telegramId, boolean inFavorites) {
}
