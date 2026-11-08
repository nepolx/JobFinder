package ru.vacancy.notifier.db;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import ru.vacancy.notifier.model.ChangeType;
import ru.vacancy.notifier.model.Recipient;
import ru.vacancy.notifier.model.Vacancy;

/**
 * Схему ведёт db-service (Flyway), Notifier её не создаёт и не меняет.
 * Ожидаемые колонки notification_log: change_id, changed_at, user_id (ключ) и
 * status ('pending'|'sent'|'failed'|'blocked'), attempts, last_error, sent_at.
 * Если названия отличаются, правится только SQL в этом файле.
 */
@Repository
public class JdbcNotificationRepository implements NotificationRepository {

    // Кому слать о НОВОЙ вакансии: активные подписки, под которые она подходит.
    // NULL в подписке означает «без ограничения по этому критерию».
    private static final String SQL_SUBSCRIBERS = """
            SELECT DISTINCT s.user_id
            FROM subscriptions s
            JOIN users u     ON u.id = s.user_id
            JOIN vacancies v ON v.id = ?
            WHERE s.is_active
              AND NOT u.is_blocked
              AND (s.region_code     IS NULL OR s.region_code = v.region_code)
              AND (s.salary_min      IS NULL OR COALESCE(v.salary_max, v.salary_min) >= s.salary_min)
              AND (s.experience_from IS NULL OR COALESCE(v.experience_years, 0) >= s.experience_from)
              AND (s.experience_to   IS NULL OR COALESCE(v.experience_years, 0) <= s.experience_to)
              AND (s.schedule_code   IS NULL OR s.schedule_code = v.schedule_code)
              AND (s.employment_code IS NULL OR s.employment_code = v.employment_code)
              AND (s.query_tsq IS NULL OR numnode(s.query_tsq) = 0 OR s.query_tsq @@ v.search_vector)
            """;

    // Кому слать об ИЗМЕНЕНИИ или закрытии: тем, у кого вакансия в избранном.
    private static final String SQL_FAVORITE_OWNERS = """
            SELECT f.user_id
            FROM user_favorites f
            JOIN users u ON u.id = f.user_id
            WHERE f.vacancy_id = ? AND NOT u.is_blocked
            """;

    private static final String SQL_CREATE_PENDING = """
            INSERT INTO notification_log (change_id, changed_at, user_id)
            VALUES (?, ?, ?)
            ON CONFLICT (change_id, user_id, changed_at) DO NOTHING
            """;

    private static final String SQL_FETCH_PENDING = """
            SELECT n.user_id, u.telegram_id
            FROM notification_log n
            JOIN users u ON u.id = n.user_id
            WHERE n.change_id = ? AND n.changed_at = ? AND n.status = 'pending' AND NOT u.is_blocked
            ORDER BY n.user_id
            """;

    private static final String KEY = " WHERE change_id = ? AND changed_at = ? AND user_id = ?";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcNotificationRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Optional<Vacancy> findVacancy(UUID vacancyId) {
        List<Vacancy> rows = jdbc.query(
                """
                SELECT id, job_name, employer_name, region_name, salary_min, salary_max, schedule, vac_url
                FROM vacancies WHERE id = ?
                """,
                (rs, i) -> new Vacancy(
                        rs.getObject("id", UUID.class),
                        rs.getString("job_name"),
                        rs.getString("employer_name"),
                        rs.getString("region_name"),
                        (Integer) rs.getObject("salary_min"),
                        (Integer) rs.getObject("salary_max"),
                        rs.getString("schedule"),
                        rs.getString("vac_url")),
                vacancyId);
        return rows.stream().findFirst();
    }

    @Override
    public List<Integer> findRecipientUserIds(UUID vacancyId, ChangeType type) {
        String sql = type == ChangeType.CREATED ? SQL_SUBSCRIBERS : SQL_FAVORITE_OWNERS;
        return jdbc.query(sql, (rs, i) -> rs.getInt(1), vacancyId);
    }

    @Override
    public void createPending(long changeId, OffsetDateTime changedAt, Collection<Integer> userIds) {
        jdbc.batchUpdate(SQL_CREATE_PENDING, userIds, 500, (ps, userId) -> {
            ps.setLong(1, changeId);
            ps.setObject(2, changedAt);
            ps.setInt(3, userId);
        });
    }

    @Override
    public List<Recipient> fetchPending(long changeId, OffsetDateTime changedAt) {
        return jdbc.query(
                SQL_FETCH_PENDING,
                (rs, i) -> new Recipient(changeId, changedAt, rs.getInt("user_id"), rs.getLong("telegram_id")),
                changeId, changedAt);
    }

    @Override
    public void markSent(Recipient r) {
        jdbc.update("UPDATE notification_log SET status = 'sent', sent_at = now(), "
                + "attempts = attempts + 1, last_error = NULL" + KEY,
                r.changeId(), r.changedAt(), r.userId());
    }

    @Override
    public void markFailed(Recipient r, String error) {
        jdbc.update("UPDATE notification_log SET status = 'failed', attempts = attempts + 1, last_error = ?" + KEY,
                truncate(error), r.changeId(), r.changedAt(), r.userId());
    }

    @Override
    public void markBlocked(Recipient r) {
        tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE notification_log SET status = 'blocked', attempts = attempts + 1" + KEY,
                    r.changeId(), r.changedAt(), r.userId());
            jdbc.update("UPDATE users SET is_blocked = true WHERE id = ?", r.userId());
        });
    }

    @Override
    public void recordAttempt(Recipient r, String error) {
        jdbc.update("UPDATE notification_log SET attempts = attempts + 1, last_error = ?" + KEY,
                truncate(error), r.changeId(), r.changedAt(), r.userId());
    }

    @Override
    public int failPending(long changeId, OffsetDateTime changedAt, String error) {
        return jdbc.update(
                "UPDATE notification_log SET status = 'failed', last_error = ? "
                        + "WHERE change_id = ? AND changed_at = ? AND status = 'pending'",
                truncate(error), changeId, changedAt);
    }

    private static String truncate(String error) {
        return error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }
}
