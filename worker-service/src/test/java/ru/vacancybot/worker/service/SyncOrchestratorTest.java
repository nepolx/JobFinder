package ru.vacancybot.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.vacancybot.worker.client.dbservice.DbServiceClient;
import ru.vacancybot.worker.config.RunProperties;
import ru.vacancybot.common.dto.VacancyChangeDto;
import ru.vacancybot.common.dto.VacancyUpsertDto;
import ru.vacancybot.worker.dto.external.TrudvsemVacancyDto;
import ru.vacancybot.worker.exception.DbServiceException;
import ru.vacancybot.worker.mapper.VacancyMapper;
import ru.vacancybot.worker.mapper.VacancyMapper.MappingResult;
import ru.vacancybot.worker.publisher.OutboxPublisher;
import ru.vacancybot.worker.publisher.OutboxPublisher.DrainResult;
import ru.vacancybot.worker.service.SyncOrchestrator.RunSummary;
import ru.vacancybot.worker.service.SyncOrchestrator.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncOrchestratorTest {

    private static final Instant NOW = Instant.parse("2026-11-09T14:00:00Z");
    private static final Duration OVERLAP = Duration.ofHours(2);
    private static final Duration LOOKBACK = Duration.ofHours(24);
    private static final Duration MAX_CATCH_UP = Duration.ofHours(48);

    private static final DrainResult NOTHING = new DrainResult(0, 0, false);
    private static final VacancyUpsertDto UPSERT = new VacancyUpsertDto("id", "ООО", "77", "Москва", "Job",
            null, null, null, null, null, null, null, null, "http://x", NOW);

    private final DbServiceClient db = mock(DbServiceClient.class);
    private final VacancyWindowLoader loader = mock(VacancyWindowLoader.class);
    private final VacancyMapper mapper = mock(VacancyMapper.class);
    private final OutboxPublisher outbox = mock(OutboxPublisher.class);
    private final SyncOrchestrator orchestrator = new SyncOrchestrator(
            db, loader, mapper, outbox,
            new RunProperties(OVERLAP, LOOKBACK, MAX_CATCH_UP),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private record Window(Instant from, Instant to, List<TrudvsemVacancyDto> vacancies) {
    }

    @BeforeEach
    void defaults() {
        when(db.getCursor()).thenReturn(Optional.empty());
        when(db.getLastModified()).thenReturn(Optional.empty());
        when(outbox.drain()).thenReturn(NOTHING);
        when(loader.findLatestModified(any(Instant.class))).thenReturn(Optional.empty());
        when(mapper.mapAll(anyList())).thenReturn(new MappingResult(List.of(UPSERT), 0));
    }

    private static TrudvsemVacancyDto vacancy(String id) {
        return new TrudvsemVacancyDto(id, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static Window window(String from, String to, TrudvsemVacancyDto... vacancies) {
        return new Window(Instant.parse(from), Instant.parse(to), List.of(vacancies));
    }

    private static VacancyChangeDto change() {
        return new VacancyChangeDto(1L, NOW, "v", "created", null);
    }

    private void loaderProduces(Window... windows) {
        doAnswer(invocation -> {
            VacancyWindowLoader.WindowHandler handler = invocation.getArgument(2);
            for (Window window : windows) {
                handler.handle(window.from(), window.to(), window.vacancies());
            }
            return null;
        }).when(loader).load(any(Instant.class), any(Instant.class), any(VacancyWindowLoader.WindowHandler.class));
    }

    private void cursorIs(Instant cursor, Instant latestInApi) {
        when(db.getCursor()).thenReturn(Optional.of(cursor));
        when(loader.findLatestModified(any(Instant.class))).thenReturn(Optional.of(latestInApi));
    }

    @Test
    void firstRunStartsFromInitialLookbackWithOverlap() {
        RunSummary summary = orchestrator.run();

        assertThat(summary.status()).isEqualTo(Status.IDLE);
        verify(loader).findLatestModified(NOW.minus(LOOKBACK).minus(OVERLAP));
    }

    @Test
    void withoutSavedCursorStartsFromLatestModifiedInDatabase() {
        Instant lastModified = NOW.minus(Duration.ofHours(5));
        when(db.getLastModified()).thenReturn(Optional.of(lastModified));

        orchestrator.run();

        verify(loader).findLatestModified(lastModified.minus(OVERLAP));
    }

    @Test
    void savedCursorHasPriorityOverDatabaseMaximum() {
        Instant cursor = NOW.minus(Duration.ofHours(6));
        when(db.getCursor()).thenReturn(Optional.of(cursor));
        when(db.getLastModified()).thenReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

        orchestrator.run();

        verify(loader).findLatestModified(cursor.minus(OVERLAP));
    }

    @Test
    void veryOldCursorIsMovedForwardToMaxCatchUp() {
        when(db.getCursor()).thenReturn(Optional.of(NOW.minus(Duration.ofDays(10))));

        orchestrator.run();

        verify(loader).findLatestModified(NOW.minus(MAX_CATCH_UP).minus(OVERLAP));
    }

    @Test
    void idleWhenApiIsEmpty() {
        RunSummary summary = orchestrator.run();

        assertThat(summary.status()).isEqualTo(Status.IDLE);
        verify(loader, never()).load(any(Instant.class), any(Instant.class), any(VacancyWindowLoader.WindowHandler.class));
        verify(db, never()).saveCursor(any(Instant.class));
    }

    @Test
    void idleWhenNewestRecordIsNotNewerThanCursor() {
        Instant cursor = NOW.minus(Duration.ofHours(3));
        cursorIs(cursor, cursor);

        RunSummary summary = orchestrator.run();

        assertThat(summary.status()).isEqualTo(Status.IDLE);
        assertThat(summary.cursor()).isEqualTo(cursor);
        verify(loader, never()).load(any(Instant.class), any(Instant.class), any(VacancyWindowLoader.WindowHandler.class));
    }

    @Test
    void idleRunStillPublishesPendingEvents() {
        when(outbox.drain()).thenReturn(new DrainResult(4, 0, false));

        RunSummary summary = orchestrator.run();

        assertThat(summary.status()).isEqualTo(Status.IDLE);
        assertThat(summary.published()).isEqualTo(4);
        verify(outbox).drain();
    }

    @Test
    void loadsUpToNewestRecordAndSavesCursorAfterEachWindow() {
        Instant cursor = Instant.parse("2026-11-09T10:00:00Z");
        Instant latest = Instant.parse("2026-11-09T13:00:00Z");
        cursorIs(cursor, latest);
        loaderProduces(
                window("2026-11-09T08:00:00Z", "2026-11-09T11:00:00Z", vacancy("a")),
                window("2026-11-09T11:00:00Z", "2026-11-09T13:00:00Z", vacancy("b")));

        RunSummary summary = orchestrator.run();

        verify(loader).load(eq(cursor.minus(OVERLAP)), eq(latest), any(VacancyWindowLoader.WindowHandler.class));
        InOrder order = inOrder(db);
        order.verify(db).syncAll(anyList());
        order.verify(db).saveCursor(Instant.parse("2026-11-09T11:00:00Z"));
        order.verify(db).syncAll(anyList());
        order.verify(db).saveCursor(Instant.parse("2026-11-09T13:00:00Z"));
        assertThat(summary.status()).isEqualTo(Status.COMPLETED);
        assertThat(summary.windows()).isEqualTo(2);
        assertThat(summary.cursor()).isEqualTo(Instant.parse("2026-11-09T13:00:00Z"));
    }

    @Test
    void cursorNeverMovesBackwards() {
        Instant cursor = Instant.parse("2026-11-09T10:00:00Z");
        cursorIs(cursor, Instant.parse("2026-11-09T13:00:00Z"));
        loaderProduces(
                window("2026-11-09T08:00:00Z", "2026-11-09T09:30:00Z", vacancy("a")),
                window("2026-11-09T09:30:00Z", "2026-11-09T13:00:00Z", vacancy("b")));

        orchestrator.run();

        verify(db, never()).saveCursor(Instant.parse("2026-11-09T09:30:00Z"));
        verify(db, times(1)).saveCursor(any(Instant.class));
        verify(db).saveCursor(Instant.parse("2026-11-09T13:00:00Z"));
    }

    @Test
    void upperBoundIsNeverInTheFuture() {
        Instant cursor = NOW.minus(Duration.ofHours(1));
        cursorIs(cursor, NOW.plus(Duration.ofDays(1)));

        orchestrator.run();

        verify(loader).load(eq(cursor.minus(OVERLAP)), eq(NOW), any(VacancyWindowLoader.WindowHandler.class));
    }

    @Test
    void emptyWindowOnlyMovesTheCursor() {
        cursorIs(Instant.parse("2026-11-09T10:00:00Z"), Instant.parse("2026-11-09T13:00:00Z"));
        loaderProduces(window("2026-11-09T08:00:00Z", "2026-11-09T13:00:00Z"));

        orchestrator.run();

        verify(mapper, never()).mapAll(anyList());
        verify(db, never()).syncAll(anyList());
        verify(db).saveCursor(Instant.parse("2026-11-09T13:00:00Z"));
    }

    @Test
    void mapsWindowSyncsItAndCountsEverything() {
        cursorIs(Instant.parse("2026-11-09T10:00:00Z"), Instant.parse("2026-11-09T13:00:00Z"));
        List<TrudvsemVacancyDto> fetched = List.of(vacancy("a"), vacancy("b"), vacancy("c"));
        loaderProduces(new Window(Instant.parse("2026-11-09T08:00:00Z"), Instant.parse("2026-11-09T13:00:00Z"), fetched));
        when(mapper.mapAll(fetched)).thenReturn(new MappingResult(List.of(UPSERT), 2));
        when(db.syncAll(List.of(UPSERT))).thenReturn(List.of(change()));
        when(outbox.drain()).thenReturn(NOTHING, new DrainResult(1, 0, false));

        RunSummary summary = orchestrator.run();

        assertThat(summary.fetched()).isEqualTo(3);
        assertThat(summary.skipped()).isEqualTo(2);
        assertThat(summary.changes()).isEqualTo(1);
        assertThat(summary.published()).isEqualTo(1);
        verify(db).syncAll(List.of(UPSERT));
    }

    @Test
    void drainsPendingEventsBeforeLoading() {
        cursorIs(Instant.parse("2026-11-09T10:00:00Z"), Instant.parse("2026-11-09T13:00:00Z"));
        loaderProduces(window("2026-11-09T08:00:00Z", "2026-11-09T13:00:00Z", vacancy("a")));

        orchestrator.run();

        InOrder order = inOrder(outbox, loader);
        order.verify(outbox).drain();
        order.verify(loader).findLatestModified(any(Instant.class));
    }

    @Test
    void drainsAgainOnlyForWindowsThatProducedChanges() {
        cursorIs(Instant.parse("2026-11-09T10:00:00Z"), Instant.parse("2026-11-09T13:00:00Z"));
        loaderProduces(
                window("2026-11-09T08:00:00Z", "2026-11-09T11:00:00Z", vacancy("a")),
                window("2026-11-09T11:00:00Z", "2026-11-09T13:00:00Z", vacancy("b")));
        when(db.syncAll(anyList())).thenReturn(List.of(), List.of(change()));

        orchestrator.run();

        verify(outbox, times(2)).drain();
    }

    @Test
    void stopsDrainingAfterBrokerFailureButKeepsLoadingAndMovingCursor() {
        cursorIs(Instant.parse("2026-11-09T10:00:00Z"), Instant.parse("2026-11-09T13:00:00Z"));
        loaderProduces(
                window("2026-11-09T08:00:00Z", "2026-11-09T11:00:00Z", vacancy("a")),
                window("2026-11-09T11:00:00Z", "2026-11-09T12:00:00Z", vacancy("b")),
                window("2026-11-09T12:00:00Z", "2026-11-09T13:00:00Z", vacancy("c")));
        when(db.syncAll(anyList())).thenReturn(List.of(change()));
        when(outbox.drain()).thenReturn(NOTHING, new DrainResult(0, 0, true));

        RunSummary summary = orchestrator.run();

        verify(outbox, times(2)).drain();   // начало запуска и первое окно; дальше брокер не мучаем
        verify(db, times(3)).saveCursor(any(Instant.class));   // события в outbox, курсор не ждёт брокера
        assertThat(summary.brokerFailed()).isTrue();
        assertThat(summary.cursor()).isEqualTo(Instant.parse("2026-11-09T13:00:00Z"));
    }

    @Test
    void doesNotDrainDuringLoadingWhenBrokerAlreadyFailedAtStart() {
        cursorIs(Instant.parse("2026-11-09T10:00:00Z"), Instant.parse("2026-11-09T13:00:00Z"));
        loaderProduces(window("2026-11-09T08:00:00Z", "2026-11-09T13:00:00Z", vacancy("a")));
        when(db.syncAll(anyList())).thenReturn(List.of(change()));
        when(outbox.drain()).thenReturn(new DrainResult(0, 0, true));

        RunSummary summary = orchestrator.run();

        verify(outbox, times(1)).drain();
        assertThat(summary.brokerFailed()).isTrue();
        verify(db).saveCursor(Instant.parse("2026-11-09T13:00:00Z"));
    }

    @Test
    void failedWindowIsNotCommittedAndErrorIsRethrown() {
        cursorIs(Instant.parse("2026-11-09T10:00:00Z"), Instant.parse("2026-11-09T13:00:00Z"));
        loaderProduces(
                window("2026-11-09T08:00:00Z", "2026-11-09T11:00:00Z", vacancy("a")),
                window("2026-11-09T11:00:00Z", "2026-11-09T13:00:00Z", vacancy("b")));
        when(db.syncAll(anyList()))
                .thenReturn(List.of())
                .thenThrow(new DbServiceException("boom", 500, true));

        assertThatThrownBy(orchestrator::run).isInstanceOf(DbServiceException.class);

        verify(db, times(1)).saveCursor(any(Instant.class));
        verify(db).saveCursor(Instant.parse("2026-11-09T11:00:00Z"));
    }

    @Test
    void errorInInitialDrainStopsTheRunBeforeAnyApiCall() {
        when(outbox.drain()).thenThrow(new DbServiceException("db-service is down", 503, true));

        assertThatThrownBy(orchestrator::run).isInstanceOf(DbServiceException.class);

        verify(loader, never()).findLatestModified(any(Instant.class));
    }

    @Test
    void failedRunReleasesTheLockSoNextRunWorks() {
        when(outbox.drain()).thenThrow(new DbServiceException("down", 503, true)).thenReturn(NOTHING);

        assertThatThrownBy(orchestrator::run).isInstanceOf(DbServiceException.class);
        RunSummary second = orchestrator.run();

        assertThat(second.status()).isEqualTo(Status.IDLE);
    }

    @Test
    void secondRunStartedWhileFirstIsInProgressIsRejected() {
        cursorIs(Instant.parse("2026-11-09T10:00:00Z"), Instant.parse("2026-11-09T13:00:00Z"));
        AtomicReference<RunSummary> inner = new AtomicReference<>();
        doAnswer(invocation -> {
            inner.set(orchestrator.run());
            return null;
        }).when(loader).load(any(Instant.class), any(Instant.class), any(VacancyWindowLoader.WindowHandler.class));

        RunSummary outer = orchestrator.run();

        assertThat(outer.status()).isEqualTo(Status.COMPLETED);
        assertThat(inner.get().status()).isEqualTo(Status.ALREADY_RUNNING);
    }
}
