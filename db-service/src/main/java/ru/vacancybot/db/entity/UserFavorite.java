package ru.vacancybot.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Избранная вакансия пользователя со статусом и заметкой. Таблица user_favorites. */
@Entity
@Table(name = "user_favorites")
@IdClass(UserFavoriteId.class)
public class UserFavorite {

    /** Допустимые значения status (совпадают с CHECK в БД). */
    public static final String STATUS_SAVED = "saved";       // просто сохранена
    public static final String STATUS_APPLIED = "applied";   // откликнулся
    public static final String STATUS_WAITING = "waiting";   // жду ответа

    @Id
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Id
    @Column(name = "vacancy_id", nullable = false)
    private UUID vacancyId;

    @Column(nullable = false)
    private String status = STATUS_SAVED;

    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserFavorite() {
        // нужен JPA
    }

    public UserFavorite(Integer userId, UUID vacancyId) {
        this.userId = userId;
        this.vacancyId = vacancyId;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Integer getUserId() { return userId; }

    public UUID getVacancyId() { return vacancyId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}