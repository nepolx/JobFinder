package ru.vacancybot.bot.stub;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.vacancybot.bot.domain.Favorite;
import ru.vacancybot.bot.domain.FavoriteStatus;
import ru.vacancybot.bot.domain.Vacancy;
import ru.vacancybot.bot.port.FavoriteService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Заглушка: избранное в памяти. Заменяется реальным сервисом напарника. */
@Service
@ConditionalOnProperty(name = "bot.stubs.enabled", havingValue = "true", matchIfMissing = true)
public class StubFavoriteService implements FavoriteService {

    private final Map<Long, Map<String, Favorite>> byUser = new LinkedHashMap<>();

    @Override
    public synchronized void add(long telegramId, String vacancyId) {
        Map<String, Favorite> favs = byUser.computeIfAbsent(telegramId, k -> new LinkedHashMap<>());
        favs.putIfAbsent(vacancyId, new Favorite(vacancyOrPlaceholder(vacancyId), FavoriteStatus.SAVED));
    }

    @Override
    public synchronized void remove(long telegramId, String vacancyId) {
        Map<String, Favorite> favs = byUser.get(telegramId);
        if (favs != null) {
            favs.remove(vacancyId);
        }
    }

    @Override
    public synchronized List<Favorite> list(long telegramId) {
        return new ArrayList<>(byUser.getOrDefault(telegramId, Map.of()).values());
    }

    @Override
    public synchronized Optional<Favorite> find(long telegramId, String vacancyId) {
        return Optional.ofNullable(byUser.getOrDefault(telegramId, Map.of()).get(vacancyId));
    }

    @Override
    public synchronized void setStatus(long telegramId, String vacancyId, FavoriteStatus status) {
        Map<String, Favorite> favs = byUser.get(telegramId);
        if (favs != null && favs.containsKey(vacancyId)) {
            favs.put(vacancyId, new Favorite(favs.get(vacancyId).vacancy(), status));
        }
    }

    private static Vacancy vacancyOrPlaceholder(String id) {
        if (id.equals(SampleVacancies.NEW_VACANCY.id())) {
            return SampleVacancies.NEW_VACANCY;
        }
        if (id.equals(SampleVacancies.CHANGED_VACANCY.id())) {
            return SampleVacancies.CHANGED_VACANCY;
        }
        return new Vacancy(id, "Вакансия (данные придут от сервиса)", "—", "—", null, null, null,
                "https://trudvsem.ru/");
    }
}
