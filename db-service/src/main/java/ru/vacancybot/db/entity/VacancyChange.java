package ru.vacancybot.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Событие по вакансии (создана или изменена). Таблица vacancy_changes.
 *
 * В базе таблица партиционирована по changed_at, и первичный ключ там (id, changed_at).
 * В JPA ключом объявлен только id: записи мы только добавляем и читаем, не обновляем.
 */
@Entity
@Table(name = "vacancy_changes")
public class VacancyChange {

    public static final String CREATED = "created";
    public static final String UPDATED = "updated";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vacancy_id", nullable = false)
    private UUID vacancyId;

    @Column(name = "change_type", nullable = false)
    private String changeType;

    /** {колонка vacancies: [было, стало]}; для created равно null. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb")
    private Map<String, List<Object>> changes;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected VacancyChange() {
        // нужен JPA
    }

    public VacancyChange(UUID vacancyId, String changeType, Map<String, List<Object>> changes) {
        this.vacancyId = vacancyId;
        this.changeType = changeType;
        this.changes = changes;
    }

    @PrePersist
    void onCreate() {
        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }

    public Long getId() { return id; }

    public UUID getVacancyId() { return vacancyId; }

    public String getChangeType() { return changeType; }

    public Map<String, List<Object>> getChanges() { return changes; }

    public Instant getChangedAt() { return changedAt; }
}