package ru.vacancybot.bot.client;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.vacancybot.bot.domain.Vacancy;
import ru.vacancybot.bot.port.FeedUnavailableException;
import ru.vacancybot.bot.port.VacancyFeedService;

import java.util.Optional;

/**
 * Подборка через db-service. ПРЕДЛОЖЕНИЕ по API (в API.md пока нет, нужно согласовать с владельцем db-service):
 * <ul>
 *   <li>{@code GET /api/users/{telegramId}/feed/next}: 200 + VacancyDto или 204, если показывать нечего;</li>
 *   <li>{@code POST /api/users/{telegramId}/feed/{vacancyId}/seen}: 204, вакансия больше не показывается.</li>
 * </ul>
 * Пока эндпоинтов нет (404/405), бот отвечает «подборка недоступна» и не падает.
 */
@Service
@ConditionalOnProperty(name = "bot.stubs.enabled", havingValue = "false")
public class HttpVacancyFeedService implements VacancyFeedService {

    private static final TypeReference<VacancyResponse> ONE = new TypeReference<>() { };

    private final DbServiceClient db;

    public HttpVacancyFeedService(DbServiceClient db) {
        this.db = db;
    }

    @Override
    public Optional<Vacancy> next(long telegramId) {
        try {
            return db.callAllowNoContent("GET", "/api/users/" + telegramId + "/feed/next", null, ONE)
                    .map(VacancyResponse::toDomain);
        } catch (DbServiceException e) {
            throw unavailableOrSame(e);
        }
    }

    @Override
    public void markSeen(long telegramId, String vacancyId) {
        try {
            db.call("POST", "/api/users/" + telegramId + "/feed/" + DbServiceClient.segment(vacancyId) + "/seen", null);
        } catch (DbServiceException e) {
            throw unavailableOrSame(e);
        }
    }

    private static RuntimeException unavailableOrSame(DbServiceException e) {
        if (e.status() == 404 || e.status() == 405 || e.status() == 501) {
            return new FeedUnavailableException("feed endpoints are not available in db-service");
        }
        return e;
    }
}
