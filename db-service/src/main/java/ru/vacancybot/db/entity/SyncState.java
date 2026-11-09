package ru.vacancybot.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Служебное значение синхронизации (ключ и значение). Таблица sync_state. */
@Entity
@Table(name = "sync_state")
public class SyncState {

    @Id
    @Column(name = "state_key")
    private String key;

    @Column(name = "state_value", nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SyncState() {
        // нужен JPA
    }

    public SyncState(String key) {
        this.key = key;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        // PostgreSQL хранит микросекунды
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public String getKey() { return key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public Instant getUpdatedAt() { return updatedAt; }
}