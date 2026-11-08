package ru.vacancybot.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** Пользователь Telegram-бота. Таблица users. */
@Entity
@Table(name = "users")
public class User {

    // serial в PostgreSQL = автоинкремент, поэтому IDENTITY
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    // true, если пользователь заблокировал бота (ставит notifier)
    @Column(name = "is_blocked", nullable = false)
    private boolean blocked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // нужен JPA
    }

    public User(Long telegramId) {
        this.telegramId = telegramId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Integer getId() { return id; }

    public Long getTelegramId() { return telegramId; }

    public void setTelegramId(Long telegramId) { this.telegramId = telegramId; }

    public boolean isBlocked() { return blocked; }

    public void setBlocked(boolean blocked) { this.blocked = blocked; }

    public Instant getCreatedAt() { return createdAt; }
}