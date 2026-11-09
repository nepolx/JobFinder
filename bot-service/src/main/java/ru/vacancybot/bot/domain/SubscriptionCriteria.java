package ru.vacancybot.bot.domain;

/** Критерии подписки (как SubscriptionDto в common). null — «не важно». Коды: Experience, ScheduleType, EmploymentType. */
public record SubscriptionCriteria(
        String title,
        String regionCode,
        Integer salaryMin,
        String experienceCode,
        String scheduleCode,
        String employmentCode,
        String keywords) {

    public boolean isEmpty() {
        return title == null && regionCode == null && salaryMin == null
                && experienceCode == null
                && scheduleCode == null && employmentCode == null && keywords == null;
    }
}
