package ru.vacancybot.bot.stub;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.vacancybot.bot.domain.Vacancy;
import ru.vacancybot.bot.port.VacancyFeedService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Заглушка: подборка из примеров в памяти, оценённые вакансии не повторяются. */
@Service
@ConditionalOnProperty(name = "bot.stubs.enabled", havingValue = "true", matchIfMissing = true)
public class StubVacancyFeedService implements VacancyFeedService {

    private final Map<Long, Set<String>> seen = new HashMap<>();

    @Override
    public synchronized Optional<Vacancy> next(long telegramId) {
        Set<String> done = seen.getOrDefault(telegramId, Set.of());
        return SampleVacancies.FEED.stream().filter(v -> !done.contains(v.id())).findFirst();
    }

    @Override
    public synchronized void markSeen(long telegramId, String vacancyId) {
        seen.computeIfAbsent(telegramId, k -> new HashSet<>()).add(vacancyId);
    }
}
