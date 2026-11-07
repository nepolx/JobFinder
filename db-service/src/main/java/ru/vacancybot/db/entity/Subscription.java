package ru.vacancybot.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Подписка пользователя на поиск вакансий. Таблица subscriptions.
 *
 * Колонка query_tsq (tsquery) генерируется базой из title и keywords,
 * поэтому в сущность она намеренно НЕ добавлена.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    private String title;

    @Column(name = "region_code")
    private String regionCode;

    @Column(name = "salary_min")
    private Integer salaryMin;

    @Column(name = "experience_from")
    private Integer experienceFrom;

    @Column(name = "experience_to")
    private Integer experienceTo;

    @Column(name = "schedule_code")
    private String scheduleCode;

    @Column(name = "employment_code")
    private String employmentCode;

    private String keywords;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Subscription() {
        // нужен JPA
    }

    public Subscription(Integer userId) {
        this.userId = userId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Integer getId() { return id; }

    public Integer getUserId() { return userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public Integer getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Integer salaryMin) { this.salaryMin = salaryMin; }

    public Integer getExperienceFrom() { return experienceFrom; }
    public void setExperienceFrom(Integer experienceFrom) { this.experienceFrom = experienceFrom; }

    public Integer getExperienceTo() { return experienceTo; }
    public void setExperienceTo(Integer experienceTo) { this.experienceTo = experienceTo; }

    public String getScheduleCode() { return scheduleCode; }
    public void setScheduleCode(String scheduleCode) { this.scheduleCode = scheduleCode; }

    public String getEmploymentCode() { return employmentCode; }
    public void setEmploymentCode(String employmentCode) { this.employmentCode = employmentCode; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
}