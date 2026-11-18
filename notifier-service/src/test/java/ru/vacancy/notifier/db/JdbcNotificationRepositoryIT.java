package ru.vacancy.notifier.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.vacancy.notifier.model.Recipient;

/**
 * Проверка SQL на настоящем PostgreSQL (нужен Docker; без него тест пропускается).
 * Схема берётся из src/test/resources/test-schema.sql (годовые партиции 2026-2032 и default, как у db-service):
 * замените её на миграции Flyway из db-service.
 * Запуск: mvn verify.
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcNotificationRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID VID = UUID.fromString("3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d");
    static final UUID OTHER_VID = UUID.fromString("aaaaaaaa-6a4d-4e0a-9c55-0d1f2a3b4c5d");
    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-08T12:00:00Z");

    static DriverManagerDataSource dataSource;
    JdbcTemplate jdbc;
    JdbcNotificationRepository repo;

    @BeforeAll
    static void createSchema() throws Exception {
        dataSource = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        try (Connection c = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("test-schema.sql"));
        }
    }

    @BeforeEach
    void cleanTables() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE notification_log, vacancy_changes, user_favorites, subscriptions, vacancies, users "
                + "RESTART IDENTITY CASCADE");
        repo = new JdbcNotificationRepository(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        insertVacancy(VID, "Python разработчик");
    }

    // ---- помощники ----

    void insertVacancy(UUID id, String jobName) {
        jdbc.update("""
                INSERT INTO vacancies (id, employer_name, region_code, region_name, job_name,
                                       salary_min, salary_max, vac_url, modified_at)
                VALUES (?, 'ООО Ромашка', '77', 'Москва', ?, 100000, 150000, 'https://x.ru/1', now())
                """, id, jobName);
    }

    int user(long telegramId, boolean blocked) {
        return jdbc.queryForObject(
                "INSERT INTO users (telegram_id, is_blocked) VALUES (?, ?) RETURNING id",
                Integer.class, telegramId, blocked);
    }

    int user(long telegramId) {
        return user(telegramId, false);
    }

    void subscribe(int userId, boolean active, String regionCode, Integer salaryMin, String keywords) {
        jdbc.update("INSERT INTO subscriptions (user_id, is_active, region_code, salary_min, keywords) "
                + "VALUES (?, ?, ?, ?, ?)", userId, active, regionCode, salaryMin, keywords);
    }

    void subscribeToEverything(int userId) {
        subscribe(userId, true, null, null, null);
    }

    void favorite(int userId, UUID vacancyId) {
        jdbc.update("INSERT INTO user_favorites (user_id, vacancy_id) VALUES (?, ?)", userId, vacancyId);
    }

    /** Запись в истории изменений вакансии; возвращает change_id. */
    long change(UUID vacancyId, OffsetDateTime at) {
        return jdbc.queryForObject(
                "INSERT INTO vacancy_changes (vacancy_id, changed_at, change_type) VALUES (?, ?, 'created') RETURNING id",
                Long.class, vacancyId, at);
    }

    void logRow(long changeId, OffsetDateTime at, int userId, String status) {
        jdbc.update("INSERT INTO notification_log (change_id, changed_at, user_id, status) VALUES (?, ?, ?, ?)",
                changeId, at, userId, status);
    }

    Set<Integer> subscribers(boolean onlyNotYetNotified) {
        return Set.copyOf(repo.findSubscriberUserIds(VID, onlyNotYetNotified));
    }

    String status(Recipient r) {
        return jdbc.queryForObject(
                "SELECT status FROM notification_log WHERE change_id = ? AND changed_at = ? AND user_id = ?",
                String.class, r.changeId(), r.changedAt(), r.userId());
    }

    // ---- подбор получателей ----

    @Test
    void subscribersAreOnlyThoseWhoseCriteriaMatch() {
        int okRegionKeyword = user(1);
        int wrongRegion = user(2);
        int tooHighSalary = user(3);
        int noCriteria = user(4);
        int inactive = user(5);
        int blocked = user(6, true);
        int wrongKeyword = user(7);

        subscribe(okRegionKeyword, true, "77", 90000, "python");
        subscribe(wrongRegion, true, "78", null, null);
        subscribe(tooHighSalary, true, null, 200000, null);
        subscribeToEverything(noCriteria);                     // «всё подряд»
        subscribe(inactive, false, "77", null, null);
        subscribe(blocked, true, "77", null, null);
        subscribe(wrongKeyword, true, null, null, "бухгалтер");

        assertEquals(Set.of(okRegionKeyword, noCriteria), subscribers(false));
    }

    @Test
    void userWithTwoMatchingSubscriptionsIsReturnedOnce() {
        int u = user(1);
        subscribe(u, true, "77", null, null);
        subscribe(u, true, null, null, "python");
        assertEquals(List.of(u), repo.findSubscriberUserIds(VID, false));
    }

    @Test
    void favoriteOwnersAreReturnedRegardlessOfSubscription() {
        int fan = user(1);
        int stranger = user(2);
        int blockedFan = user(3, true);
        subscribeToEverything(stranger);                        // подписан, но вакансии в избранном нет
        favorite(fan, VID);
        favorite(blockedFan, VID);

        assertEquals(List.of(fan), repo.findFavoriteOwnerUserIds(VID));
    }

    // ---- «новая» против «изменилась» ----

    @Test
    void notYetNotifiedSkipsThoseWhoAlreadyGotTheVacancy() {
        insertVacancy(OTHER_VID, "Бухгалтер");
        int gotItBefore = user(1);
        int never = user(2);
        int failedBefore = user(3);
        int gotOtherVacancy = user(4);
        for (int u : new int[] {gotItBefore, never, failedBefore, gotOtherVacancy}) {
            subscribeToEverything(u);
        }
        long changeOfThisVacancy = change(VID, T0.minusDays(3));
        long changeOfOtherVacancy = change(OTHER_VID, T0.minusDays(3));
        logRow(changeOfThisVacancy, T0.minusDays(3), gotItBefore, "sent");
        logRow(changeOfThisVacancy, T0.minusDays(3), failedBefore, "failed");   // не доставлено: «новая» ещё актуальна
        logRow(changeOfOtherVacancy, T0.minusDays(3), gotOtherVacancy, "sent"); // другая вакансия не в счёт

        assertEquals(Set.of(gotItBefore, never, failedBefore, gotOtherVacancy), subscribers(false));
        assertEquals(Set.of(never, failedBefore, gotOtherVacancy), subscribers(true));
    }

    @Test
    void notifiedInPreviousYearPartitionStillCounts() {
        // уведомление ушло в 2026 году (партиция 2026), вакансия обновляется в 2027 (партиция 2027)
        int u = user(1);
        subscribeToEverything(u);
        OffsetDateTime lastYear = OffsetDateTime.parse("2026-03-01T10:00:00Z");
        logRow(change(VID, lastYear), lastYear, u, "sent");

        assertTrue(subscribers(true).isEmpty());
    }

    @Test
    void fetchPendingMarksWhoHasTheVacancyInFavorites() {
        int fan = user(11);
        int newcomer = user(22);
        favorite(fan, VID);
        repo.createPending(5, T0, List.of(fan, newcomer));

        List<Recipient> pending = repo.fetchPending(5, T0, VID);
        assertEquals(2, pending.size());
        assertTrue(pending.stream().filter(r -> r.userId() == fan).findFirst().orElseThrow().inFavorites());
        assertFalse(pending.stream().filter(r -> r.userId() == newcomer).findFirst().orElseThrow().inFavorites());
    }

    // ---- журнал и годовые партиции ----

    @Test
    void pendingLifecycleIsIdempotent() {
        int u1 = user(11);
        int u2 = user(22);
        repo.createPending(5, T0, List.of(u1, u2));
        repo.createPending(5, T0, List.of(u1, u2));              // повтор события

        List<Recipient> pending = repo.fetchPending(5, T0, VID);
        assertEquals(Set.of(11L, 22L), pending.stream().map(Recipient::telegramId).collect(Collectors.toSet()));

        repo.markSent(pending.get(0));
        repo.createPending(5, T0, List.of(u1, u2));              // отправленное не воскресает
        assertEquals(1, repo.fetchPending(5, T0, VID).size());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM notification_log", Integer.class));
        assertEquals("sent", status(pending.get(0)));
    }

    @Test
    void recordsOfDifferentYearsLiveInTheirOwnPartitions() {
        int u = user(1);
        OffsetDateTime nextYear = OffsetDateTime.parse("2027-01-08T12:00:00Z");
        repo.createPending(5, T0, List.of(u));
        repo.createPending(5, nextYear, List.of(u));             // тот же change_id, другая партиция

        assertEquals(1, repo.fetchPending(5, T0, VID).size());
        assertEquals(1, repo.fetchPending(5, nextYear, VID).size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_log_2026", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_log_2027", Integer.class));
    }

    @Test
    void eventBeyondYearlyPartitionsLandsInDefaultPartitionAndIsStillFound() {
        int u = user(1);
        OffsetDateTime farFuture = OffsetDateTime.parse("2040-01-01T00:00:00Z");   // вне 2026-2032
        repo.createPending(5, farFuture, List.of(u));

        assertEquals(1, repo.fetchPending(5, farFuture, VID).size());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_log_default", Integer.class));
    }

    @Test
    void markBlockedFlagsUserAndHidesFromPending() {
        int u = user(1);
        repo.createPending(9, T0, List.of(u));
        Recipient r = repo.fetchPending(9, T0, VID).get(0);
        repo.markBlocked(r);

        assertTrue(jdbc.queryForObject("SELECT is_blocked FROM users WHERE id = ?", Boolean.class, u));
        assertEquals("blocked", status(r));
        assertTrue(repo.fetchPending(9, T0, VID).isEmpty());
    }

    @Test
    void recordAttemptKeepsRecordPendingAndCountsAttempts() {
        int u = user(1);
        repo.createPending(4, T0, List.of(u));
        Recipient r = repo.fetchPending(4, T0, VID).get(0);
        repo.recordAttempt(r, "telegram 503");

        assertEquals("pending", status(r));
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM notification_log WHERE user_id = ?", Integer.class, u));
    }

    @Test
    void failPendingReturnsNumberOfUpdatedRows() {
        int u1 = user(1);
        int u2 = user(2);
        repo.createPending(3, T0, List.of(u1, u2));
        assertEquals(2, repo.failPending(3, T0, "gave up"));
        assertEquals(0, repo.failPending(3, T0, "gave up"));
    }

    @Test
    void findVacancyReturnsEmptyForUnknownId() {
        assertTrue(repo.findVacancy(UUID.randomUUID()).isEmpty());
        assertEquals("Python разработчик", repo.findVacancy(VID).orElseThrow().jobName());
    }
}
