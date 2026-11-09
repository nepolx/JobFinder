package ru.vacancybot.bot.fsm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import ru.vacancybot.bot.domain.Experience;
import ru.vacancybot.bot.domain.SubscriptionCriteria;

import java.io.Serializable;

/** Черновик подписки в контексте сессии. null — параметр пропущен («не важно»). */
public class SubscriptionDraft implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;
    private String regionCode;
    private Integer salaryMin;
    private Experience experience;
    private String scheduleCode;
    private String employmentCode;
    private String keywords;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public Integer getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(Integer salaryMin) {
        this.salaryMin = salaryMin;
    }

    public Experience getExperience() {
        return experience;
    }

    public void setExperience(Experience experience) {
        this.experience = experience;
    }

    public String getScheduleCode() {
        return scheduleCode;
    }

    public void setScheduleCode(String scheduleCode) {
        this.scheduleCode = scheduleCode;
    }

    public String getEmploymentCode() {
        return employmentCode;
    }

    public void setEmploymentCode(String employmentCode) {
        this.employmentCode = employmentCode;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    /** Сбрасывает параметр, который задаётся на данном шаге («Пропустить»). */
    public void clear(DialogState state) {
        switch (state) {
            case SEARCH_TITLE -> title = null;
            case SEARCH_REGION -> regionCode = null;
            case SEARCH_SALARY -> salaryMin = null;
            case SEARCH_EXPERIENCE -> experience = null;
            case SEARCH_SCHEDULE -> scheduleCode = null;
            case SEARCH_EMPLOYMENT -> employmentCode = null;
            case SEARCH_KEYWORDS -> keywords = null;
            default -> throw new IllegalArgumentException("Not a question state: " + state);
        }
    }

    @JsonIgnore
    public boolean isEmpty() {
        return toCriteria().isEmpty();
    }

    public SubscriptionCriteria toCriteria() {
        return new SubscriptionCriteria(
                title, regionCode, salaryMin,
                experience == null ? null : experience.code(),
                scheduleCode, employmentCode, keywords);
    }
}
