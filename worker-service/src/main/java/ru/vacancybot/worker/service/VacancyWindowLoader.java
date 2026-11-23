package ru.vacancybot.worker.service;

import jakarta.annotation.PreDestroy;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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

    private ExecutorService pagePool;

    public void load(Instant from, Instant to, WindowHandler handler) {
        Instant start = from.truncatedTo(ChronoUnit.SECONDS);
        Instant upperBound = to.truncatedTo(ChronoUnit.SECONDS);
        if (start.isAfter(upperBound)) {
            log.debug("Nothing to load: from {} is after to {}", start, upperBound);
            return;
        }

        while (true) {
            Instant end = start.plus(properties.windowSize()).truncatedTo(ChronoUnit.SECONDS);
            if (end.isAfter(upperBound)) {
                end = upperBound;
            }
            loadWindow(start, end, handler);
            if (!end.isBefore(upperBound)) {
                return;
            }
            start = end;
        }
    }

    private void loadWindow(Instant start, Instant end, WindowHandler handler) {
        for (int attempt = 0; ; attempt++) {
            VacancyPage first = fetch(0, start, end);
            if (isSplittable(start, end)
                    && (first.exceedsReadableRange() || first.total() > properties.maxWindowRecords())) {
                split(start, end, first.total(), handler);
                return;
            }
            if (first.exceedsReadableRange()) {
                split(start, end, first.total(), handler);
                return;
            }

            List<VacancyPage> pages = fetchAllPages(first, start, end);
            VacancyPage last = pages.getLast();
            List<TrudvsemVacancyDto> raw = new ArrayList<>();
            for (VacancyPage page : pages) {
                raw.addAll(page.vacancies());
            }

            if (last.exceedsReadableRange()) {
                split(start, end, last.total(), handler);
                return;
            }

            List<TrudvsemVacancyDto> vacancies = deduplicate(raw);
            long withoutId = raw.stream().filter(vacancy -> vacancy.id() == null).count();
            long received = vacancies.size() + withoutId;
            if (received < last.total()) {
                if (attempt >= properties.maxReloadAttempts()) {
                    throw new IllegalStateException(
                            "Window [" + start + ", " + end + "] is incomplete: received "
                                    + received + " of " + last.total());
                }
                log.warn("Window [{}, {}] changed while loading: received {} of {}. Reloading (attempt {})",
                        start, end, received, last.total(), attempt + 1);
                continue;
            }

            log.info("Window [{}, {}]: {} vacancies ({} after deduplication)",
                    start, end, raw.size(), vacancies.size());
            handler.handle(start, end, vacancies);
            return;
        }
    }

    private List<VacancyPage> fetchAllPages(VacancyPage first, Instant start, Instant end) {
        List<VacancyPage> pages = new ArrayList<>();
        pages.add(first);

        if (properties.pageConcurrency() <= 1) {
            VacancyPage last = first;
            while (last.hasNext()) {
                last = fetch(last.nextPage(), start, end);
                pages.add(last);
            }
            return pages;
        }

        int lastPage = Math.min(first.totalPages() - 1, TrudvsemApiClient.MAX_PAGE);
        List<CompletableFuture<VacancyPage>> futures = new ArrayList<>();
        for (int page = 1; page <= lastPage; page++) {
            int number = page;
            futures.add(CompletableFuture.supplyAsync(() -> fetch(number, start, end), pagePool()));
        }
        try {
            for (CompletableFuture<VacancyPage> future : futures) {
                pages.add(future.join());
            }
        } catch (CompletionException e) {
            futures.forEach(future -> future.cancel(true));
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
        return pages;
    }

    private synchronized ExecutorService pagePool() {
        if (pagePool == null) {
            AtomicInteger counter = new AtomicInteger();
            pagePool = Executors.newFixedThreadPool(properties.pageConcurrency(), task -> {
                Thread thread = new Thread(task, "trudvsem-page-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
        }
        return pagePool;
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (pagePool != null) {
            pagePool.shutdownNow();
        }
    }

    private static boolean isSplittable(Instant start, Instant end) {
        return Duration.between(start, end).getSeconds() > 0;
    }

    private void split(Instant start, Instant end, int total, WindowHandler handler) {
        long seconds = Duration.between(start, end).getSeconds();
        if (seconds == 0) {
            throw new IllegalStateException(
                    "Cannot split window of one second [" + start + "]: " + total
                            + " vacancies exceed the readable range");
        }
        log.info("Window [{}, {}] has {} vacancies, splitting into smaller windows", start, end, total);
        if (seconds == 1) {
            loadWindow(start, start, handler);
            loadWindow(end, end, handler);
            return;
        }
        Instant middle = start.plusSeconds(seconds / 2);
        loadWindow(start, middle, handler);
        loadWindow(middle, end, handler);
    }

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

    public Optional<Instant> findLatestModified(Instant from) {
        Optional<Instant> latest = withRetry("latest modified", () -> client.findLatestModified(from));
        sleep(properties.pauseBetweenRequests().toMillis());
        return latest;
    }

    private VacancyPage fetch(int page, Instant from, Instant to) {
        VacancyPage result = withRetry("page " + page, () -> client.fetchPage(page, from, to));
        sleep(properties.pauseBetweenRequests().toMillis());
        return result;
    }

    private <T> T withRetry(String what, Supplier<T> call) {
        for (int attempt = 0; ; attempt++) {
            try {
                return call.get();
            } catch (TrudvsemApiException e) {
                if (!e.isRetryable() || attempt >= properties.maxRetries()) {
                    throw e;
                }
                long delay = properties.retryBaseDelay().toMillis() * (1L << attempt);
                log.warn("Trudvsem request '{}' failed (attempt {}): {}. Retrying in {} ms",
                        what, attempt + 1, e.getMessage(), delay);
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