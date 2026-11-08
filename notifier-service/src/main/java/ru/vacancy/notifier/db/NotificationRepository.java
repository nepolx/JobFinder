package ru.vacancy.notifier.db;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ru.vacancy.notifier.model.ChangeType;
import ru.vacancy.notifier.model.Recipient;
import ru.vacancy.notifier.model.Vacancy;

/**
 * Доступ к данным. Журнал notification_log партиционирован по changed_at, ключ записи
 * (change_id, changed_at, user_id): все операции с журналом идут по полному ключу.
 */
public interface NotificationRepository {

    Optional<Vacancy> findVacancy(UUID vacancyId);

    /** CREATED: подходящие активные подписки; UPDATED/CLOSED: владельцы избранного. */
    List<Integer> findRecipientUserIds(UUID vacancyId, ChangeType type);

    /** Идемпотентно: повторный вызов не создаёт дублей. */
    void createPending(long changeId, OffsetDateTime changedAt, Collection<Integer> userIds);

    List<Recipient> fetchPending(long changeId, OffsetDateTime changedAt);

    void markSent(Recipient recipient);

    void markFailed(Recipient recipient, String error);

    /** Помечает запись blocked и ставит users.is_blocked = true (в одной транзакции). */
    void markBlocked(Recipient recipient);

    /** Временная неудача: запись остаётся pending, увеличивается счётчик попыток. */
    void recordAttempt(Recipient recipient, String error);

    /** Переводит все ещё pending записи изменения в failed, возвращает их число. */
    int failPending(long changeId, OffsetDateTime changedAt, String error);
}
