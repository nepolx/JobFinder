package ru.vacancybot.bot.port;

import ru.vacancybot.bot.domain.Vacancy;

import java.util.Optional;

/**
 * Подборка вакансий для режима «👍/👎»: следующая вакансия, подходящая под активные подписки пользователя,
 * которую он ещё не оценивал. Нужен эндпоинт в db-service (см. README, раздел «Подборка»).
 */
public interface VacancyFeedService {

    /** Следующая непросмотренная вакансия; пусто — показывать больше нечего. */
    Optional<Vacancy> next(long telegramId);

    /** Вакансия оценена (👍 или 👎), больше в подборке не показывается. Идемпотентно. */
    void markSeen(long telegramId, String vacancyId);
}
