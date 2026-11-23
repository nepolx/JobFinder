package ru.vacancybot.worker.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.vacancybot.worker.client.trudvsem.TrudvsemApiClient;
import ru.vacancybot.worker.config.SyncProperties;
import ru.vacancybot.worker.dto.external.TrudvsemVacancyDto;
import ru.vacancybot.worker.exception.TrudvsemApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VacancyWindowLoaderTest {

    private static final Instant BASE = Instant.parse("2026-11-20T10:00:00Z");

    static class FakeApi extends TrudvsemApiClient {

        record Row(String id, Instant modified) {
        }

        final List<Row> data = new CopyOnWriteArrayList<>();
        final AtomicInteger requests = new AtomicInteger();
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();
        final AtomicInteger maxPage = new AtomicInteger();
        final Map<Integer, AtomicInteger> requestsPerPage = new ConcurrentHashMap<>();
        final Map<Integer, Integer> transientFailures = new ConcurrentHashMap<>();
        final Set<Integer> permanentFailures = ConcurrentHashMap.newKeySet();
        volatile Runnable beforeSecondRequest;
        volatile int inflateTotalBy;
        volatile int latestFailures;
        volatile long latencyMs;

        FakeApi() {
            super(null, null);
        }

        @Override
        public VacancyPage fetchPage(int page, Instant from, Instant to) {
            return fetchPage(page, 100, from, to);
        }

        @Override
        public VacancyPage fetchPage(int page, int limit, Instant from, Instant to) {
            if (page > MAX_PAGE) {
                throw new IllegalArgumentException("portal does not serve offset >= 100");
            }
            int number = requests.incrementAndGet();
            if (number == 2 && beforeSecondRequest != null) {
                Runnable hook = beforeSecondRequest;
                beforeSecondRequest = null;
                hook.run();
            }
            maxPage.accumulateAndGet(page, Math::max);
            requestsPerPage.computeIfAbsent(page, key -> new AtomicInteger()).incrementAndGet();

            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            try {
                if (latencyMs > 0) {
                    try {
                        Thread.sleep(latencyMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                }
                if (permanentFailures.contains(page)) {
                    throw new TrudvsemApiException("HTTP 400", 400, false);
                }
                Integer left = transientFailures.computeIfPresent(page, (key, value) -> value - 1);
                if (left != null && left >= 0) {
                    throw new TrudvsemApiException("timeout", (Integer) null, true);
                }

                List<Row> window = new ArrayList<>();
                for (Row row : data) {
                    if (!row.modified().isBefore(from) && (to == null || !row.modified().isAfter(to))) {
                        window.add(row);
                    }
                }
                window.sort(Comparator.comparing(Row::modified).reversed().thenComparing(Row::id,
                        Comparator.nullsFirst(Comparator.naturalOrder())));

                int start = Math.min(page * limit, window.size());
                int end = Math.min(start + limit, window.size());
                List<TrudvsemVacancyDto> vacancies = new ArrayList<>();
                for (Row row : window.subList(start, end)) {
                    vacancies.add(dto(row.id()));
                }
                return new VacancyPage(vacancies, window.size() + inflateTotalBy, page, limit);
            } finally {
                inFlight.decrementAndGet();
            }
        }

        @Override
        public Optional<Instant> findLatestModified(Instant from) {
            if (latestFailures > 0) {
                latestFailures--;
                throw new TrudvsemApiException("timeout", (Integer) null, true);
            }
            return data.stream().map(Row::modified).filter(time -> !time.isBefore(from)).max(Comparator.naturalOrder());
        }

        void addRows(int count, long secondsPerRow) {
            for (int i = 0; i < count; i++) {
                data.add(new Row(String.format("id-%05d", data.size()), BASE.plusSeconds(i * secondsPerRow)));
            }
        }
    }

    private static TrudvsemVacancyDto dto(String id) {
        return new TrudvsemVacancyDto(id, null, null, null, null, null, null, null, null, null, null, null);
    }

    private VacancyWindowLoader loader;

    @AfterEach
    void tearDown() {
        if (loader != null) {
            loader.shutdown();
        }
    }

    private static SyncProperties props(int concurrency, int maxRetries, int maxWindowRecords, int maxReloads) {
        return new SyncProperties(Duration.ofHours(3), maxReloads, maxRetries, Duration.ofMillis(1),
                Duration.ZERO, maxWindowRecords, concurrency);
    }

    private List<String> loadIds(FakeApi api, SyncProperties properties, Instant to) {
        loader = new VacancyWindowLoader(api, properties);
        List<String> ids = new ArrayList<>();
        loader.load(VacancyWindowLoaderTest.BASE, to, (windowFrom, windowTo, vacancies) -> vacancies.forEach(v -> ids.add(v.id())));
        return ids;
    }

    private static FakeApi apiWithRows(int count, long secondsPerRow) {
        FakeApi api = new FakeApi();
        api.addRows(count, secondsPerRow);
        return api;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void loadsEveryRecordNewestFirst(int concurrency) {
        FakeApi api = apiWithRows(1000, 1);
        api.latencyMs = 20;

        List<String> ids = loadIds(api, props(concurrency, 0, 10_000, 2), BASE.plusSeconds(3000));

        List<String> expected = new ArrayList<>();
        for (int i = 999; i >= 0; i--) {
            expected.add(String.format("id-%05d", i));
        }
        assertThat(ids).isEqualTo(expected);
        assertThat(api.requests.get()).isEqualTo(10);   // 1000 записей = 10 страниц, лишних запросов нет
        if (concurrency == 1) {
            assertThat(api.maxInFlight.get()).isEqualTo(1);
        } else {
            assertThat(api.maxInFlight.get()).isBetween(2, 3);
        }
    }

    @Test
    void singlePageNeedsSingleRequest() {
        FakeApi api = apiWithRows(50, 1);

        List<String> ids = loadIds(api, props(3, 0, 10_000, 2), BASE.plusSeconds(3000));

        assertThat(ids).hasSize(50);
        assertThat(api.requests.get()).isEqualTo(1);
    }

    @Test
    void windowsGoForwardShareBoundaryAndEmptyOnesAreReported() {
        FakeApi api = new FakeApi();
        loader = new VacancyWindowLoader(api, props(1, 0, 10_000, 2));
        List<Instant[]> windows = new ArrayList<>();

        loader.load(BASE, BASE.plusSeconds(7 * 3600), (from, to, vacancies) -> {
            assertThat(vacancies).isEmpty();
            windows.add(new Instant[]{from, to});
        });

        assertThat(windows).hasSize(3);
        assertThat(windows.get(0)).containsExactly(BASE, BASE.plusSeconds(3 * 3600));
        assertThat(windows.get(1)).containsExactly(BASE.plusSeconds(3 * 3600), BASE.plusSeconds(6 * 3600));
        assertThat(windows.get(2)).containsExactly(BASE.plusSeconds(6 * 3600), BASE.plusSeconds(7 * 3600));
    }

    @Test
    void doesNothingWhenFromIsAfterTo() {
        FakeApi api = apiWithRows(10, 1);
        loader = new VacancyWindowLoader(api, props(1, 0, 10_000, 2));
        List<Integer> calls = new ArrayList<>();

        loader.load(BASE.plusSeconds(100), BASE, (from, to, vacancies) -> calls.add(1));

        assertThat(calls).isEmpty();
        assertThat(api.requests.get()).isZero();
    }

    @Test
    void windowIsSplitWhenItHasMoreRecordsThanConfigured() {
        FakeApi api = apiWithRows(250, 14);
        loader = new VacancyWindowLoader(api, props(1, 0, 100, 2));
        Set<String> seen = new HashSet<>();
        List<Integer> sizes = new ArrayList<>();

        loader.load(BASE, BASE.plusSeconds(3600), (from, to, vacancies) -> {
            sizes.add(vacancies.size());
            vacancies.forEach(v -> seen.add(v.id()));
        });

        assertThat(seen).hasSize(250);
        assertThat(sizes.size()).isGreaterThan(1);
        assertThat(sizes).allSatisfy(size -> assertThat(size).isLessThanOrEqualTo(100));
        assertThat(api.maxPage.get()).isZero();
    }

    @Test
    void windowThatDoesNotFitIntoOneQueryIsSplitAndNeverAsksForPage100() {
        FakeApi api = new FakeApi();
        for (int i = 0; i < 10_500; i++) {
            api.data.add(new FakeApi.Row(String.format("id-%05d", i), BASE.plusSeconds(i / 3)));   // по 3 в секунду
        }

        List<String> ids = loadIds(api, props(3, 0, 10_000, 2), BASE.plusSeconds(3600));

        assertThat(new HashSet<>(ids)).hasSize(10_500);
        assertThat(api.maxPage.get()).isLessThanOrEqualTo(99);
    }

    @Test
    void largeSingleSecondIsLoadedWhenItFitsIntoOneQuery() {
        FakeApi api = new FakeApi();
        for (int i = 0; i < 150; i++) {
            api.data.add(new FakeApi.Row(String.format("id-%05d", i), BASE));
        }

        List<String> ids = loadIds(api, props(1, 0, 100, 2), BASE.plusSeconds(600));

        assertThat(new HashSet<>(ids)).hasSize(150);
    }

    @Test
    void singleSecondBeyondReadableRangeFails() {
        FakeApi api = new FakeApi();
        for (int i = 0; i < 10_001; i++) {
            api.data.add(new FakeApi.Row(String.format("id-%05d", i), BASE));
        }
        loader = new VacancyWindowLoader(api, props(1, 0, 10_000, 2));

        assertThatThrownBy(() -> loader.load(BASE, BASE.plusSeconds(10), (from, to, vacancies) -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot split");
    }

    @Test
    void temporaryFailureOfOnePageIsRetriedInsideParallelLoading() {
        FakeApi api = apiWithRows(1000, 1);
        api.transientFailures.put(4, 2);

        List<String> ids = loadIds(api, props(3, 3, 10_000, 2), BASE.plusSeconds(3000));

        assertThat(ids).hasSize(1000);
        assertThat(api.requestsPerPage.get(4).get()).isEqualTo(3);
    }

    @Test
    void givesUpWhenRetriesAreExhausted() {
        FakeApi api = apiWithRows(1000, 1);
        api.transientFailures.put(4, 10);
        loader = new VacancyWindowLoader(api, props(3, 2, 10_000, 2));

        assertThatThrownBy(() -> loader.load(BASE, BASE.plusSeconds(3000), (from, to, vacancies) -> { }))
                .isInstanceOfSatisfying(TrudvsemApiException.class, e -> assertThat(e.isRetryable()).isTrue());
    }

    @Test
    void permanentErrorIsRethrownWithoutRetry() {
        FakeApi api = apiWithRows(1000, 1);
        api.permanentFailures.add(6);
        loader = new VacancyWindowLoader(api, props(3, 3, 10_000, 2));

        assertThatThrownBy(() -> loader.load(BASE, BASE.plusSeconds(3000), (from, to, vacancies) -> { }))
                .isInstanceOfSatisfying(TrudvsemApiException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.isRetryable()).isFalse();
                });
        assertThat(api.requestsPerPage.get(6).get()).isEqualTo(1);
    }

    @Test
    void failedWindowIsNotPassedToTheHandler() {
        FakeApi api = apiWithRows(1000, 1);
        api.permanentFailures.add(2);
        loader = new VacancyWindowLoader(api, props(1, 0, 10_000, 2));
        List<Integer> handled = new ArrayList<>();

        assertThatThrownBy(() -> loader.load(BASE, BASE.plusSeconds(3000), (from, to, vacancies) -> handled.add(1)))
                .isInstanceOf(TrudvsemApiException.class);

        assertThat(handled).isEmpty();
    }

    @Test
    void windowIsReloadedWhenNewRecordsShiftThePagesDuringLoading() {
        FakeApi api = apiWithRows(250, 1);
        // после первой страницы в окно «доезжают» 5 новых записей: все следующие страницы сдвигаются
        api.beforeSecondRequest = () -> {
            for (int i = 250; i < 255; i++) {
                api.data.add(new FakeApi.Row(String.format("id-%05d", i), BASE.plusSeconds(i)));
            }
        };

        List<String> ids = loadIds(api, props(1, 0, 10_000, 2), BASE.plusSeconds(3000));

        assertThat(ids).hasSize(255);
        assertThat(new HashSet<>(ids)).hasSize(255);
        assertThat(ids).contains("id-00254", "id-00250");
        assertThat(api.requests.get()).isGreaterThan(3);
    }

    @Test
    void failsWhenWindowStaysIncompleteAfterAllReloads() {
        FakeApi api = apiWithRows(250, 1);
        api.inflateTotalBy = 5;
        loader = new VacancyWindowLoader(api, props(1, 0, 10_000, 1));

        assertThatThrownBy(() -> loader.load(BASE, BASE.plusSeconds(3000), (from, to, vacancies) -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incomplete");
    }

    @Test
    void recordsWithoutIdAreDroppedButDoNotTriggerReloads() {
        FakeApi api = apiWithRows(10, 1);
        api.data.add(new FakeApi.Row(null, BASE.plusSeconds(20)));

        List<String> ids = loadIds(api, props(1, 0, 10_000, 0), BASE.plusSeconds(3000));

        assertThat(ids).hasSize(10).doesNotContainNull();
        assertThat(api.requests.get()).isEqualTo(1);
    }

    @Test
    void findLatestModifiedRetriesTemporaryErrors() {
        FakeApi api = apiWithRows(10, 1);
        api.latestFailures = 2;
        loader = new VacancyWindowLoader(api, props(1, 3, 10_000, 2));

        assertThat(loader.findLatestModified(BASE)).contains(BASE.plusSeconds(9));
    }

    @Test
    void findLatestModifiedGivesUpWhenRetriesAreExhausted() {
        FakeApi api = apiWithRows(10, 1);
        api.latestFailures = 10;
        loader = new VacancyWindowLoader(api, props(1, 1, 10_000, 2));

        assertThatThrownBy(() -> loader.findLatestModified(BASE)).isInstanceOf(TrudvsemApiException.class);
    }

    @Test
    void findLatestModifiedIsEmptyWhenNothingIsNewer() {
        FakeApi api = apiWithRows(10, 1);
        loader = new VacancyWindowLoader(api, props(1, 0, 10_000, 2));

        assertThat(loader.findLatestModified(BASE.plusSeconds(100))).isEmpty();
    }
}
