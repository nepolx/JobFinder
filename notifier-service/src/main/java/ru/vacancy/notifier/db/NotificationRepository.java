package ru.vacancy.notifier.db;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import ru.vacancy.notifier.model.Recipient;
import ru.vacancy.notifier.model.Vacancy;

public interface NotificationRepository {

    Optional<Vacancy> findVacancy(UUID vacancyId);

    List<Integer> findSubscriberUserIds(UUID vacancyId, boolean onlyNotYetNotified);

    List<Integer> findFavoriteOwnerUserIds(UUID vacancyId);

    void createPending(long changeId, OffsetDateTime changedAt, Collection<Integer> userIds);

    List<Recipient> fetchPending(long changeId, OffsetDateTime changedAt, UUID vacancyId);

    void markSent(Recipient recipient);

    void markFailed(Recipient recipient, String error);
    
    void markBlocked(Recipient recipient);

    void recordAttempt(Recipient recipient, String error);

    int failPending(long changeId, OffsetDateTime changedAt, String error);
}
