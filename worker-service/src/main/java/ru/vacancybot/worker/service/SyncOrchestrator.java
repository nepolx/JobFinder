package ru.vacancybot.worker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vacancybot.worker.client.dbservice.DbServiceClient;
import ru.vacancybot.worker.config.RunProperties;
import ru.vacancybot.worker.dto.dbservice.VacancyChangeDto;
import ru.vacancybot.worker.dto.external.TrudvsemVacancyDto;
import ru.vacancybot.worker.mapper.VacancyMapper;
import ru.vacancybot.worker.mapper.VacancyMapper.MappingResult;
import ru.vacancybot.worker.publisher.OutboxPublisher;
import ru.vacancybot.worker.publisher.OutboxPublisher.DrainResult;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class SyncOrchestrator {

    public enum Status {
        COMPLETED,
        IDLE,
        ALREADY_RUNNING
    }

    public record RunSummary(Status status, int windows, int fetched, int skipped, int changes,
                             int published, boolean brokerFailed, Instant cursor) {
    }

    private static final class RunState {
        Instant cursor;
        int windows;
        int fetched;
        int skipped;
        int changes;
        int published;
        boolean brokerFailed;
    }

    private final DbServiceClient dbClient;
    private final VacancyWindowLoader loader;
    private final VacancyMapper mapper;
    private final OutboxPublisher outbox;
    private final RunProperties properties;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public SyncOrchestrator(DbServiceClient dbClient,
                            VacancyWindowLoader loader,
                            VacancyMapper mapper,
                            OutboxPublisher outbox,
                            RunProperties properties,
                            Clock clock) {
        this.dbClient = dbClient;
        this.loader = loader;
        this.mapper = mapper;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    public RunSummary run() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Previous sync run is still in progress, skipping");
            return new RunSummary(Status.ALREADY_RUNNING, 0, 0, 0, 0, 0, false, null);
        }
        try {
            return doRun();
        } finally {
            running.set(false);
        }
    }

    private RunSummary doRun() {
        Instant now = clock.instant();
        RunState state = new RunState();

        // 1. события, оставшиеся с прошлых запусков (в том числе при недоступном брокере)
        DrainResult pending = outbox.drain();
        state.published += pending.published();
        state.brokerFailed = pending.brokerFailed();

        // 2. курсор
        state.cursor = readCursor(now);
        Instant from = state.cursor.minus(properties.overlap());

        // 3. верхняя граница: самая новая запись в API, но не из будущего
        Optional<Instant> latest = loader.findLatestModified(from);
        Instant to = latest.map(value -> value.isAfter(now) ? now : value).orElse(null);
        if (to == null || !to.isAfter(state.cursor)) {
            log.info("No new data in API (cursor {}, latest {})", state.cursor, latest.orElse(null));
            return summary(Status.IDLE, state);
        }

        // 4. загрузка окнами
        log.info("Sync started: from {} to {} (cursor {})", from, to, state.cursor);
        loader.load(from, to, (windowFrom, windowTo, vacancies) -> handleWindow(state, windowTo, vacancies));

        RunSummary result = summary(Status.COMPLETED, state);
        log.info("Sync completed: windows={}, fetched={}, skipped={}, changes={}, published={}, brokerFailed={}, cursor={}",
                result.windows(), result.fetched(), result.skipped(), result.changes(),
                result.published(), result.brokerFailed(), result.cursor());
        return result;
    }

    private void handleWindow(RunState state, Instant windowTo, List<TrudvsemVacancyDto> vacancies) {
        state.windows++;
        state.fetched += vacancies.size();

        if (!vacancies.isEmpty()) {
            MappingResult mapped = mapper.mapAll(vacancies);
            state.skipped += mapped.skipped();

            List<VacancyChangeDto> changes = dbClient.syncAll(mapped.vacancies());
            state.changes += changes.size();

            // если брокер уже не отвечал в этом запуске, повторно ждать таймаут на каждом окне не нужно:
            // события лежат в outbox и уйдут в начале следующего запуска
            if (!changes.isEmpty() && !state.brokerFailed) {
                DrainResult drained = outbox.drain();
                state.published += drained.published();
                state.brokerFailed = drained.brokerFailed();
            }
        }

        // окна идут вперёд, но первое окно начинается раньше курсора (overlap): назад курсор не двигаем
        if (windowTo.isAfter(state.cursor)) {
            dbClient.saveCursor(windowTo);
            state.cursor = windowTo;
        }
    }

    private Instant readCursor(Instant now) {
        Optional<Instant> saved = dbClient.getCursor();
        if (saved.isPresent()) {
            return saved.get();
        }
        Optional<Instant> lastModified = dbClient.getLastModified();
        if (lastModified.isPresent()) {
            log.info("No saved cursor, starting from the latest modified vacancy in the database: {}",
                    lastModified.get());
            return lastModified.get();
        }
        Instant initial = now.minus(properties.initialLookback());
        log.info("First run: loading the last {} (from {})", properties.initialLookback(), initial);
        return initial;
    }

    private static RunSummary summary(Status status, RunState state) {
        return new RunSummary(status, state.windows, state.fetched, state.skipped, state.changes,
                state.published, state.brokerFailed, state.cursor);
    }
}