package ru.vacancybot.db.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Составной первичный ключ избранного: (user_id, vacancy_id).
 * Используется как @IdClass в UserFavorite. Имена полей должны совпадать
 * с именами @Id-полей в UserFavorite.
 */
public class UserFavoriteId implements Serializable {

    private Integer userId;
    private UUID vacancyId;

    public UserFavoriteId() {
        // нужен JPA
    }

    public UserFavoriteId(Integer userId, UUID vacancyId) {
        this.userId = userId;
        this.vacancyId = vacancyId;
    }

    public Integer getUserId() { return userId; }

    public UUID getVacancyId() { return vacancyId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserFavoriteId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(vacancyId, that.vacancyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, vacancyId);
    }
}