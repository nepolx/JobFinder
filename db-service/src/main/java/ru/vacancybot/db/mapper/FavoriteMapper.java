package ru.vacancybot.db.mapper;

import ru.vacancybot.common.dto.FavoriteDto;
import ru.vacancybot.db.entity.UserFavorite;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Преобразование между FavoriteDto и UserFavorite.
 *
 * В DTO статус пишется заглавными (SAVED / APPLIED / WAITING),
 * в базе строчными (saved / applied / waiting). У избранного нет своего id
 * (ключ составной), поэтому id в DTO всегда null: запись определяется
 * парой (telegramUserId, vacancyId).
 */
public final class FavoriteMapper {

    private static final Set<String> STATUSES = Set.of(
            UserFavorite.STATUS_SAVED,
            UserFavorite.STATUS_APPLIED,
            UserFavorite.STATUS_WAITING);

    private FavoriteMapper() {
    }

    public static FavoriteDto toDto(UserFavorite f, Long telegramUserId) {
        return new FavoriteDto(
                null,
                telegramUserId,
                f.getVacancyId().toString(),
                f.getStatus().toUpperCase(Locale.ROOT),
                f.getNote());
    }

    public static UUID parseVacancyId(String vacancyId) {
        if (vacancyId == null || vacancyId.isBlank()) {
            throw new IllegalArgumentException("vacancyId обязателен");
        }
        try {
            return UUID.fromString(vacancyId.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("vacancyId должен быть UUID: " + vacancyId);
        }
    }

    public static String toDbStatus(String status) {
        String value = status.trim().toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(value)) {
            throw new IllegalArgumentException(
                    "Неизвестный статус: " + status + " (допустимо: SAVED, APPLIED, WAITING)");
        }
        return value;
    }
}