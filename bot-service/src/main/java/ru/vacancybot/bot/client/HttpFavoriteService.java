package ru.vacancybot.bot.client;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.vacancybot.bot.domain.Favorite;
import ru.vacancybot.bot.domain.FavoriteStatus;
import ru.vacancybot.bot.domain.Vacancy;
import ru.vacancybot.bot.port.FavoriteService;
import ru.vacancybot.common.dto.FavoriteDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Избранное через REST db-service (API.md, часть A). В ответах db-service только FavoriteDto,
 * карточку вакансии собираем вторым запросом GET /api/vacancies?ids=... (один на все id).
 */
@Service
@ConditionalOnProperty(name = "bot.stubs.enabled", havingValue = "false")
public class HttpFavoriteService implements FavoriteService {

    private static final TypeReference<FavoriteDto> ONE = new TypeReference<>() { };
    private static final TypeReference<List<FavoriteDto>> MANY = new TypeReference<>() { };
    private static final TypeReference<List<VacancyResponse>> VACANCIES = new TypeReference<>() { };
    private static final int MAX_IDS_PER_REQUEST = 100;

    private final DbServiceClient db;

    public HttpFavoriteService(DbServiceClient db) {
        this.db = db;
    }

    @Override
    public void add(long telegramId, String vacancyId) {
        // тело не нужно: новая запись получает SAVED, повторное добавление не падает
        try {
            db.call("PUT", path(telegramId, vacancyId), null);
        } catch (DbServiceException e) {
            if (e.status() == 404) { // вакансии нет в таблице vacancies
                throw new NoSuchElementException("Vacancy " + vacancyId + " not found");
            }
            throw e;
        }
    }

    @Override
    public void remove(long telegramId, String vacancyId) {
        db.call("DELETE", path(telegramId, vacancyId), null);
    }

    @Override
    public List<Favorite> list(long telegramId) {
        List<FavoriteDto> favorites = db.call("GET", "/api/users/" + telegramId + "/favorites", null, MANY);
        Map<String, Vacancy> vacancies = loadVacancies(favorites.stream().map(FavoriteDto::vacancyId).toList());
        List<Favorite> result = new ArrayList<>();
        for (FavoriteDto dto : favorites) {
            Vacancy v = vacancies.get(dto.vacancyId());
            if (v != null) {
                result.add(new Favorite(v, statusOf(dto)));
            }
        }
        return result;
    }

    @Override
    public Optional<Favorite> find(long telegramId, String vacancyId) {
        Optional<FavoriteDto> dto = db.callOptional("GET", path(telegramId, vacancyId), null, ONE);
        if (dto.isEmpty()) {
            return Optional.empty();
        }
        Vacancy v = loadVacancies(List.of(vacancyId)).get(vacancyId);
        return v == null ? Optional.empty() : Optional.of(new Favorite(v, statusOf(dto.get())));
    }

    @Override
    public void setStatus(long telegramId, String vacancyId, FavoriteStatus status) {
        // note = null: заметку db-service не меняет
        FavoriteDto body = new FavoriteDto(null, telegramId, vacancyId, status.name(), null);
        db.call("PATCH", path(telegramId, vacancyId), body);
    }

    private Map<String, Vacancy> loadVacancies(List<String> ids) {
        Map<String, Vacancy> result = new HashMap<>();
        for (int from = 0; from < ids.size(); from += MAX_IDS_PER_REQUEST) {
            List<String> chunk = ids.subList(from, Math.min(from + MAX_IDS_PER_REQUEST, ids.size()));
            List<VacancyResponse> loaded = db.call("GET", "/api/vacancies?ids=" + String.join(",", chunk), null, VACANCIES);
            for (VacancyResponse r : loaded) {
                result.put(r.id(), r.toDomain());
            }
        }
        return result;
    }

    private static FavoriteStatus statusOf(FavoriteDto dto) {
        try {
            return FavoriteStatus.valueOf(dto.status());
        } catch (IllegalArgumentException | NullPointerException e) {
            return FavoriteStatus.SAVED;
        }
    }

    private static String path(long telegramId, String vacancyId) {
        return "/api/users/" + telegramId + "/favorites/" + DbServiceClient.segment(vacancyId);
    }
}
