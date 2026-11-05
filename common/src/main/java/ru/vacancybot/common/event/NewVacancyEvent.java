package ru.vacancybot.common.event;

/** Событие «найдена новая вакансия по подписке» (Worker -> Notifier). */
public record NewVacancyEvent(
        long chatId,
        long subscriptionId,
        String vacancyId,
        String title,
        String employer,
        String region,
        Integer salaryFrom,
        String schedule,
        String url
) {}
