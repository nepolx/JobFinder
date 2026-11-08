package ru.vacancy.notifier.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.vacancy.notifier.db.NotificationRepository;
import ru.vacancy.notifier.format.MessageFormatter;
import ru.vacancy.notifier.limit.ChatRateLimiter;
import ru.vacancy.notifier.model.ChangeEvent;
import ru.vacancy.notifier.model.ChangeType;
import ru.vacancy.notifier.model.Recipient;
import ru.vacancy.notifier.model.Vacancy;
import ru.vacancy.notifier.retry.RetryExecutor;
import ru.vacancy.notifier.telegram.BotBlockedException;
import ru.vacancy.notifier.telegram.PermanentException;
import ru.vacancy.notifier.telegram.TelegramSender;
import ru.vacancy.notifier.telegram.TemporaryException;

/**
 * Событие -> получатели -> отправка -> журнал.
 *
 * <p>Кому и что отправляется:
 * <ul>
 *   <li>created: подходящие подписки, текст «новая вакансия»;</li>
 *   <li>updated: владельцы избранного получают «изменилась» (с diff); подписчики, которым о вакансии
 *       ещё ничего не отправляли, получают её как «новую» (если включено notifySubscribersOnUpdate);</li>
 *   <li>closed: владельцы избранного.</li>
 * </ul>
 * Одно и то же событие поэтому может уйти разным людям в разном виде.
 */
public final class NotificationService implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private enum Outcome { SENT, BLOCKED, FAILED, RETRY_LATER }

    private final NotificationRepository repo;
    private final TelegramSender sender;
    private final ChatRateLimiter limiter;
    private final RetryExecutor retry;
    private final ExecutorService executor;
    private final int concurrency;
    private final boolean notifySubscribersOnUpdate;

    public NotificationService(NotificationRepository repo, TelegramSender sender, ChatRateLimiter limiter,
                               RetryExecutor retry, ExecutorService executor, int concurrency,
                               boolean notifySubscribersOnUpdate) {
        this.repo = repo;
        this.sender = sender;
        this.limiter = limiter;
        this.retry = retry;
        this.executor = executor;
        this.concurrency = concurrency;
        this.notifySubscribersOnUpdate = notifySubscribersOnUpdate;
    }

    /**
     * Идемпотентна: повторный вызов для того же события шлёт только тем, у кого запись
     * в журнале ещё pending.
     */
    @Override
    public HandleResult handle(ChangeEvent event) {
        Optional<Vacancy> vacancy = repo.findVacancy(event.vacancyId());
        if (vacancy.isEmpty()) {
            log.warn("change {}: вакансия {} не найдена, пропускаем", event.changeId(), event.vacancyId());
            return HandleResult.EMPTY;
        }

        Set<Integer> userIds = findRecipients(event);
        if (!userIds.isEmpty()) {
            repo.createPending(event.changeId(), event.changedAt(), userIds);
        }

        List<Recipient> pending = repo.fetchPending(event.changeId(), event.changedAt(), event.vacancyId());
        if (pending.isEmpty()) {
            return HandleResult.EMPTY;
        }

        String eventText = MessageFormatter.format(event, vacancy.get());
        String newVacancyText = event.type() == ChangeType.UPDATED
                ? MessageFormatter.format(event.asNewVacancy(), vacancy.get())
                : eventText;
        Semaphore semaphore = new Semaphore(concurrency);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (Recipient recipient : pending) {
            futures.add(executor.submit(() -> {
                semaphore.acquire();
                try {
                    // изменение показываем «как изменилось» только тем, у кого вакансия в избранном
                    boolean asNew = event.type() == ChangeType.UPDATED && !recipient.inFavorites();
                    return deliver(recipient, asNew ? newVacancyText : eventText);
                } finally {
                    semaphore.release();
                }
            }));
        }

        int sent = 0;
        int blocked = 0;
        int failed = 0;
        int retryLater = 0;
        Throwable failure = null;
        for (Future<Outcome> future : futures) {
            try {
                switch (future.get()) {
                    case SENT -> sent++;
                    case BLOCKED -> blocked++;
                    case FAILED -> failed++;
                    case RETRY_LATER -> retryLater++;
                }
            } catch (ExecutionException e) {
                if (failure == null) {
                    failure = e.getCause();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("прервано во время обработки события", e);
            }
        }
        if (failure != null) {
            // Непредвиденная ошибка (например, БД): уже отправленные помечены,
            // остальные подхватит повторная обработка сообщения.
            if (failure instanceof RuntimeException re) {
                throw re;
            }
            if (failure instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(failure);
        }

        log.info("change {} ({}): sent={} blocked={} failed={} retryLater={}",
                event.changeId(), event.type().value(), sent, blocked, failed, retryLater);
        return new HandleResult(sent, blocked, failed, retryLater);
    }

    private Set<Integer> findRecipients(ChangeEvent event) {
        Set<Integer> ids = new LinkedHashSet<>();
        switch (event.type()) {
            case CREATED -> ids.addAll(repo.findSubscriberUserIds(event.vacancyId(), false));
            case UPDATED -> {
                ids.addAll(repo.findFavoriteOwnerUserIds(event.vacancyId()));
                if (notifySubscribersOnUpdate) {
                    ids.addAll(repo.findSubscriberUserIds(event.vacancyId(), true));
                }
            }
            case CLOSED -> ids.addAll(repo.findFavoriteOwnerUserIds(event.vacancyId()));
        }
        return ids;
    }

    @Override
    public int giveUp(ChangeEvent event) {
        int count = repo.failPending(event.changeId(), event.changedAt(), "исчерпаны повторные попытки");
        log.error("change {}: сдаёмся, не доставлено {} уведомлений", event.changeId(), count);
        return count;
    }

    private Outcome deliver(Recipient recipient, String text) {
        try {
            retry.run(() -> {
                // Лимит берём перед КАЖДОЙ попыткой, включая повторы.
                limiter.acquire(recipient.telegramId());
                sender.sendMessage(recipient.telegramId(), text);
            });
        } catch (BotBlockedException e) {
            repo.markBlocked(recipient);
            return Outcome.BLOCKED;
        } catch (PermanentException e) {
            repo.markFailed(recipient, e.getMessage());
            return Outcome.FAILED;
        } catch (TemporaryException e) {
            repo.recordAttempt(recipient, e.getMessage());
            return Outcome.RETRY_LATER;
        }
        repo.markSent(recipient);
        return Outcome.SENT;
    }
}
