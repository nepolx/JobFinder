package ru.vacancy.notifier.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import ru.vacancy.notifier.model.ChangeType;
import ru.vacancy.notifier.model.Recipient;

/**
 * Проверка SQL на настоящем PostgreSQL (нужен Docker; без него тест пропускается).
 * Схема берётся из src/test/resources/test-schema.sql: замените её на миграции Flyway из db-service.
 * Запуск: mvn verify (или mvn test -Dtest=JdbcNotificationRepositoryIT).
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcNotificationRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    static final UUID VID = UUID.fromString("3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d");
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
        jdbc.execute("TRUNCATE notification_log, user_favorites, subscriptions, vacancies, users RESTART IDENTITY CASCADE");
        repo = new JdbcNotificationRepository(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        jdbc.update("""
                INSERT INTO vacancies (id, employer_name, region_code, region_name, job_name,
                                       salary_min, salary_max, vac_url, modified_at)
                VALUES (?, 'ООО Ромашка', '77', 'Москва', 'Python разработчик', 100000, 150000,
                        'https://x.ru/1', now())
                """, VID);
    }

    // ---- помощники ----

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

    Set<Integer> recipients(ChangeType type) {
        return Set.copyOf(repo.findRecipientUserIds(VID, type));
    }

    String status(Recipient r) {
        return jdbc.queryForObject(
                "SELECT status FROM notification_log WHERE change_id = ? AND changed_at = ? AND user_id = ?",
                String.class, r.changeId(), r.changedAt(), r.userId());
    }

    // ---- тесты ----

    @Test
    void newVacancyGoesOnlyToMatchingSubscriptions() {
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
        subscribe(noCriteria, true, null, null, null);          // «всё подряд»
        subscribe(inactive, false, "77", null, null);
        subscribe(blocked, true, "77", null, null);
        subscribe(wrongKeyword, true, null, null, "бухгалтер");

        assertEquals(Set.of(okRegionKeyword, noCriteria), recipients(ChangeType.CREATED));
    }

    @Test
    void userWithTwoMatchingSubscriptionsGetsOneNotification() {
        int u = user(1);
        subscribe(u, true, "77", null, null);
        subscribe(u, true, null, null, "python");
        assertEquals(List.of(u), repo.findRecipientUserIds(VID, ChangeType.CREATED));
    }

    @Test
    void updatesAndClosingGoToFavoritesOnly() {
        int fan = user(1);
        int stranger = user(2);
        subscribe(stranger, true, null, null, null);             // подписан, но вакансии в избранном нет
        jdbc.update("INSERT INTO user_favorites (user_id, vacancy_id) VALUES (?, ?)", fan, VID);

        assertEquals(Set.of(fan), recipients(ChangeType.UPDATED));
        assertEquals(Set.of(fan), recipients(ChangeType.CLOSED));
    }

    @Test
    void pendingLifecycleIsIdempotent() {
        int u1 = user(11);
        int u2 = user(22);
        repo.createPending(5, T0, List.of(u1, u2));
        repo.createPending(5, T0, List.of(u1, u2));              // повтор события

        List<Recipient> pending = repo.fetchPending(5, T0);
        assertEquals(Set.of(11L, 22L), pending.stream().map(Recipient::telegramId).collect(Collectors.toSet()));

        repo.markSent(pending.get(0));
        repo.createPending(5, T0, List.of(u1, u2));              // отправленное не воскресает
        assertEquals(1, repo.fetchPending(5, T0).size());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM notification_log", Integer.class));
        assertEquals("sent", status(pending.get(0)));
    }

    @Test
    void sameChangeIdWithDifferentChangedAtAreSeparateRows() {
        int u = user(1);
        repo.createPending(5, T0, List.of(u));
        repo.createPending(5, T0.plusMonths(2), List.of(u));     // другая партиция/месяц
        assertEquals(1, repo.fetchPending(5, T0).size());
        assertEquals(1, repo.fetchPending(5, T0.plusMonths(2)).size());
    }

    @Test
    void markBlockedFlagsUserAndHidesFromPending() {
        int u = user(1);
        repo.createPending(9, T0, List.of(u));
        Recipient r = repo.fetchPending(9, T0).get(0);
        repo.markBlocked(r);

        assertTrue(jdbc.queryForObject("SELECT is_blocked FROM users WHERE id = ?", Boolean.class, u));
        assertEquals("blocked", status(r));
        assertTrue(repo.fetchPending(9, T0).isEmpty());
    }

    @Test
    void recordAttemptKeepsRecordPendingAndCountsAttempts() {
        int u = user(1);
        repo.createPending(4, T0, List.of(u));
        Recipient r = repo.fetchPending(4, T0).get(0);
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
