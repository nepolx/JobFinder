package ru.vacancybot.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vacancybot.worker.client.trudvsem.TrudvsemApiClient;
import ru.vacancybot.worker.client.trudvsem.TrudvsemApiClient.VacancyPage;
import ru.vacancybot.worker.config.SyncProperties;
import ru.vacancybot.worker.dto.external.TrudvsemVacancyDto;
import ru.vacancybot.worker.exception.TrudvsemApiException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VacancyWindowLoader {

    @FunctionalInterface
    public interface WindowHandler {
        void handle(Instant from, Instant to, List<TrudvsemVacancyDto> vacancies);
    }

    private final TrudvsemApiClient client;
    private final SyncProperties properties;

    public void load(Instant from, Instant to, WindowHandler handler) {
        Instant start = from.truncatedTo(ChronoUnit.SECONDS);
        Instant limit = to.truncatedTo(ChronoUnit.SECONDS);
        if (start.isAfter(limit)) {
            log.debug("Nothing to load: from {} is after to {}", start, limit);
            return;
        }

        while (true) {
            Instant end = start.plus(properties.windowSize()).truncatedTo(ChronoUnit.SECONDS);
            if (end.isAfter(limit)) {
                end = limit;
            }
            loadWindow(start, end, handler);
            if (!end.isBefore(limit)) {
                return;
            }
            start = end;
        }
    }

    private void loadWindow(Instant start, Instant end, WindowHandler handler) {
        for (int attempt = 0; ; attempt++) {
            VacancyPage first = fetch(0, start, end);
            if (first.exceedsReadableRange()) {
                split(start, end, first.total(), handler);
                return;
            }

            List<TrudvsemVacancyDto> raw = new ArrayList<>(first.vacancies());
            VacancyPage last = first;
            while (last.hasNext()) {
                last = fetch(last.nextPage(), start, end);
                raw.addAll(last.vacancies());
            }

            // пока шли по страницам, окно могло вырасти
            if (last.exceedsReadableRange()) {
                split(start, end, last.total(), handler);
                return;
            }

            // меньше записей, чем сообщает total: страницы сдвинулись из-за обновления данных
            if (raw.size() < last.total()) {
                if (attempt >= properties.maxReloadAttempts()) {
                    throw new IllegalStateException(
                            "Window [" + start + ", " + end + "] is incomplete: received "
                                    + raw.size() + " of " + last.total());
                }
                log.warn("Window [{}, {}] changed while loading: received {} of {}. Reloading (attempt {})",
                        start, end, raw.size(), last.total(), attempt + 1);
                continue;
            }

            List<TrudvsemVacancyDto> vacancies = deduplicate(raw);
            log.info("Window [{}, {}]: {} vacancies ({} after deduplication)",
                    start, end, raw.size(), vacancies.size());
            handler.handle(start, end, vacancies);
            return;
        }
    }

    /** Делит окно пополам; левая половина обрабатывается раньше правой. */
    private void split(Instant start, Instant end, int total, WindowHandler handler) {
        long seconds = Duration.between(start, end).getSeconds();
        if (seconds == 0) {
            throw new IllegalStateException(
                    "Cannot split window of one second [" + start + "]: " + total
                            + " vacancies exceed the readable range");
        }
        log.info("Window [{}, {}] has {} vacancies, more than readable in one query. Splitting", start, end, total);
        if (seconds == 1) {
            loadWindow(start, start, handler);
            loadWindow(end, end, handler);
            return;
        }
        Instant middle = start.plusSeconds(seconds / 2);
        loadWindow(start, middle, handler);
        loadWindow(middle, end, handler);
    }

    /** Одна и та же вакансия может прийти дважды при сдвиге страниц; оставляем первую (самую новую). */
    private List<TrudvsemVacancyDto> deduplicate(List<TrudvsemVacancyDto> raw) {
        Map<String, TrudvsemVacancyDto> byId = new LinkedHashMap<>();
        for (TrudvsemVacancyDto vacancy : raw) {
            if (vacancy.id() == null) {
                log.warn("Skipping vacancy without id: {}", vacancy.jobName());
                continue;
            }
            byId.putIfAbsent(vacancy.id(), vacancy);
        }
        return new ArrayList<>(byId.values());
    }

    /** Запрос страницы с повторами при временных ошибках и паузой после успешного запроса. */
    private VacancyPage fetch(int page, Instant from, Instant to) {
        for (int attempt = 0; ; attempt++) {
            try {
                VacancyPage result = client.fetchPage(page, from, to);
                sleep(properties.pauseBetweenRequests().toMillis());
                return result;
            } catch (TrudvsemApiException e) {
                if (!e.isRetryable() || attempt >= properties.maxRetries()) {
                    throw e;
                }
                long delay = properties.retryBaseDelay().toMillis() * (1L << attempt);
                log.warn("Trudvsem request failed (page {}, attempt {}): {}. Retrying in {} ms",
                        page, attempt + 1, e.getMessage(), delay);
                sleep(delay);
            }
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Loading interrupted", e);
        }
    }
}