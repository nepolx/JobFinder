package ru.vacancybot.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Вакансия с портала trudvsem.ru. Таблица vacancies.
 *
 * id приходит из API (не генерируется базой).
 * Колонка search_vector (tsvector) генерируется базой из job_name, duty и requirements,
 * поэтому в сущность намеренно НЕ добавлена. Поиск по ней пишем нативным SQL.
 */
@Entity
@Table(name = "vacancies")
public class Vacancy {

    @Id
    private UUID id;

    @Column(name = "employer_name", nullable = false)
    private String employerName;

    @Column(name = "region_code", nullable = false)
    private String regionCode;

    @Column(name = "region_name", nullable = false)
    private String regionName;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "salary_min")
    private Integer salaryMin;

    // 0 из API превращается в NULL (делает тот, кто пишет вакансию)
    @Column(name = "salary_max")
    private Integer salaryMax;

    private String schedule;

    @Column(name = "schedule_code")
    private String scheduleCode;

    @Column(name = "employment_code")
    private String employmentCode;

    @Column(name = "experience_years")
    private Integer experienceYears;

    private String duty;

    private String requirements;

    @Column(name = "vac_url", nullable = false)
    private String vacUrl;

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    protected Vacancy() {
        // нужен JPA
    }

    public Vacancy(UUID id) {
        this.id = id;
    }

    public UUID getId() { return id; }

    public String getEmployerName() { return employerName; }
    public void setEmployerName(String employerName) { this.employerName = employerName; }

    public String getRegionCode() { return regionCode; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public String getRegionName() { return regionName; }
    public void setRegionName(String regionName) { this.regionName = regionName; }

    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }

    public Integer getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Integer salaryMin) { this.salaryMin = salaryMin; }

    public Integer getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Integer salaryMax) { this.salaryMax = salaryMax; }

    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }

    public String getScheduleCode() { return scheduleCode; }
    public void setScheduleCode(String scheduleCode) { this.scheduleCode = scheduleCode; }

    public String getEmploymentCode() { return employmentCode; }
    public void setEmploymentCode(String employmentCode) { this.employmentCode = employmentCode; }

    public Integer getExperienceYears() { return experienceYears; }
    public void setExperienceYears(Integer experienceYears) { this.experienceYears = experienceYears; }

    public String getDuty() { return duty; }
    public void setDuty(String duty) { this.duty = duty; }

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }

    public String getVacUrl() { return vacUrl; }
    public void setVacUrl(String vacUrl) { this.vacUrl = vacUrl; }

    public Instant getModifiedAt() { return modifiedAt; }
    public void setModifiedAt(Instant modifiedAt) { this.modifiedAt = modifiedAt; }
}