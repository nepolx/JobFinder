package ru.vacancy.notifier.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.vacancy.notifier.db.NotificationRepository;
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

class NotificationServiceTest {

    static final UUID VID = UUID.fromString("3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d");
    static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-08T12:00:00Z");
    static final Vacancy VACANCY = new Vacancy(VID, "Python-разработчик", "ООО Ромашка", "Москва",
            100000, 150000, "Полный день", "https://trudvsem.ru/v/1");

    // ---- заглушки ----

    /** Хранит журнал в памяти и повторяет контракт JdbcNotificationRepository. */
    static class FakeRepo implements NotificationRepository {
        Vacancy vacancy = VACANCY;
        final List<Integer> subscribers = new ArrayList<>();   // у кого вакансия подходит под подписку
        final Set<Integer> favorites = new HashSet<>();        // у кого она в избранном
        final Set<Integer> blockedUsers = new HashSet<>();
        final Map<String, String> status = new LinkedHashMap<>();   // "change|changedAt|user" -> status

        FakeRepo(Integer... subscribedUsers) {
            subscribers.addAll(List.of(subscribedUsers));
        }

        static String key(long changeId, OffsetDateTime changedAt, int userId) {
            return changeId + "|" + changedAt + "|" + userId;
        }

        static String key(Recipient r) {
            return key(r.changeId(), r.changedAt(), r.userId());
        }

        long telegramId(int userId) {
            return 1000 + userId;
        }

        /** Как в БД: «уже уведомляли» = есть запись sent по любому изменению этой вакансии. */
        boolean alreadyNotified(int userId) {
            return status.entrySet().stream()
                    .anyMatch(e -> e.getKey().endsWith("|" + userId) && e.getValue().equals("sent"));
        }

        @Override
        public Optional<Vacancy> findVacancy(UUID vacancyId) {
            return Optional.ofNullable(vacancy);
        }

        @Override
        public List<Integer> findSubscriberUserIds(UUID vacancyId, boolean onlyNotYetNotified) {
            return subscribers.stream()
                    .filter(u -> !blockedUsers.contains(u))
                    .filter(u -> !onlyNotYetNotified || !alreadyNotified(u))
                    .toList();
        }

        @Override
        public List<Integer> findFavoriteOwnerUserIds(UUID vacancyId) {
            return favorites.stream().filter(u -> !blockedUsers.contains(u)).sorted().toList();
        }

        @Override
        public void createPending(long changeId, OffsetDateTime changedAt, Collection<Integer> ids) {
            for (int u : ids) {
                status.putIfAbsent(key(changeId, changedAt, u), "pending");
            }
        }

        @Override
        public List<Recipient> fetchPending(long changeId, OffsetDateTime changedAt, UUID vacancyId) {
            Set<Integer> everyone = new java.util.TreeSet<>(subscribers);
            everyone.addAll(favorites);
            List<Recipient> result = new ArrayList<>();
            for (int u : everyone) {
                if ("pending".equals(status.get(key(changeId, changedAt, u))) && !blockedUsers.contains(u)) {
                    result.add(new Recipient(changeId, changedAt, u, telegramId(u), favorites.contains(u)));
                }
            }
            return result;
        }

        @Override
        public void markSent(Recipient r) {
            status.put(key(r), "sent");
        }

        @Override
        public void markFailed(Recipient r, String error) {
            status.put(key(r), "failed");
        }

        @Override
        public void markBlocked(Recipient r) {
            status.put(key(r), "blocked");
            blockedUsers.add(r.userId());
        }

        @Override
        public void recordAttempt(Recipient r, String error) {
            // запись остаётся pending
        }

        @Override
        public int failPending(long changeId, OffsetDateTime changedAt, String error) {
            int n = 0;
            for (Map.Entry<String, String> e : status.entrySet()) {
                if (e.getKey().startsWith(changeId + "|" + changedAt + "|") && e.getValue().equals("pending")) {
                    e.setValue("failed");
                    n++;
                }
            }
            return n;
        }

        Map<Integer, String> statusByUser(long changeId) {
            Map<Integer, String> result = new TreeMap<>();
            Set<Integer> everyone = new HashSet<>(subscribers);
            everyone.addAll(favorites);
            for (int u : everyone) {
                String s = status.get(key(changeId, T0, u));
                if (s != null) {
                    result.put(u, s);
                }
            }
            return result;
        }
    }

    /** failing(chatId, ошибки...): ошибки бросаются по очереди перед успехом. */
    static class FakeSender implements TelegramSender {
        final Map<Long, Queue<RuntimeException>> script = new HashMap<>();
        final List<Long> calls = new ArrayList<>();
        final List<Long> sentTo = new ArrayList<>();
        final Map<Long, String> textTo = new HashMap<>();

        FakeSender failing(long chatId, RuntimeException... errors) {
            script.put(chatId, new LinkedList<>(List.of(errors)));
            return this;
        }

        @Override
        public void sendMessage(long chatId, String text) {
            calls.add(chatId);
            Queue<RuntimeException> queue = script.get(chatId);
            if (queue != null && !queue.isEmpty()) {
                throw queue.poll();
            }
            sentTo.add(chatId);
            textTo.put(chatId, text);
        }

        long count(long chatId) {
            return calls.stream().filter(c -> c == chatId).count();
        }
    }

    static class FakeLimiter implements ChatRateLimiter {
        final List<Long> calls = new ArrayList<>();

        @Override
        public void acquire(long chatId) {
            calls.add(chatId);
        }
    }

    // ---- настройка ----

    ExecutorService executor;

    @BeforeEach
    void setUp() {
        // один поток: обработка последовательна, заглушкам не нужна потокобезопасность
        executor = Executors.newSingleThreadExecutor();
    }

    NotificationService service(FakeRepo repo, FakeSender sender, FakeLimiter limiter, boolean subscribersOnUpdate) {
        RetryExecutor retry = new RetryExecutor(3, 1.0, 30.0, seconds -> { }, d -> d);
        return new NotificationService(repo, sender, limiter, retry, executor, 5, subscribersOnUpdate);
    }

    NotificationService service(FakeRepo repo, FakeSender sender, FakeLimiter limiter) {
        return service(repo, sender, limiter, true);
    }

    NotificationService service(FakeRepo repo, FakeSender sender) {
        return service(repo, sender, new FakeLimiter(), true);
    }

    static ChangeEvent created(long changeId) {
        return new ChangeEvent(changeId, VID, ChangeType.CREATED, T0, Map.of());
    }

    static ChangeEvent updated(long changeId) {
        return new ChangeEvent(changeId, VID, ChangeType.UPDATED, T0,
                Map.of("salary_min", List.<Object>of(50000, 70000)));
    }

    static ChangeEvent closed(long changeId) {
        return new ChangeEvent(changeId, VID, ChangeType.CLOSED, T0, Map.of());
    }

    // ---- кому и в каком виде ----

    @Test
    void createdGoesToMatchingSubscribersAsNewVacancy() {
        FakeRepo repo = new FakeRepo(1, 2);
        repo.favorites.add(9);                      // избранное при created не учитывается
        FakeSender sender = new FakeSender();
        service(repo, sender).handle(created(1));

        assertEquals(List.of(1001L, 1002L), sender.sentTo);
        assertTrue(sender.textTo.get(1001L).contains("Новая вакансия"));
    }

    @Test
    void sameUpdateIsSentAsChangedToFavoriteOwnersAndAsNewToSubscribersWhoNeverSawIt() {
        FakeRepo repo = new FakeRepo(1, 2, 3);
        repo.favorites.add(1);                      // 1: уже видел и добавил в избранное
        repo.status.put(FakeRepo.key(0, T0.minusDays(5), 3), "sent");   // 3: уже получал её раньше, в избранное не брал
        FakeSender sender = new FakeSender();
        HandleResult result = service(repo, sender).handle(updated(7));

        // 1: «изменилась» с diff
        String forFavoriteOwner = sender.textTo.get(1001L);
        assertTrue(forFavoriteOwner.contains("Вакансия из избранного изменилась"), forFavoriteOwner);
        assertTrue(forFavoriteOwner.contains("Что изменилось"));
        // 2: пользователь вакансию не видел, для него она «новая», без diff
        String forNewcomer = sender.textTo.get(1002L);
        assertTrue(forNewcomer.contains("Новая вакансия"), forNewcomer);
        assertFalse(forNewcomer.contains("Что изменилось"));
        // 3: уже получал и не в избранном: молчим, чтобы не повторять «новую» при каждой правке
        assertFalse(sender.textTo.containsKey(1003L));
        assertEquals(2, result.sent());
    }

    @Test
    void favoriteOwnerWhoIsAlsoSubscriberGetsExactlyOneMessage() {
        FakeRepo repo = new FakeRepo(1);
        repo.favorites.add(1);
        FakeSender sender = new FakeSender();
        service(repo, sender).handle(updated(7));

        assertEquals(1L, sender.count(1001));
        assertTrue(sender.textTo.get(1001L).contains("Что изменилось"));
    }

    @Test
    void subscribersAreNotNotifiedAboutUpdatesWhenFeatureIsOff() {
        FakeRepo repo = new FakeRepo(1, 2);
        repo.favorites.add(1);
        FakeSender sender = new FakeSender();
        service(repo, sender, new FakeLimiter(), false).handle(updated(7));

        assertEquals(List.of(1001L), sender.sentTo);
    }

    @Test
    void closedGoesOnlyToFavoriteOwners() {
        FakeRepo repo = new FakeRepo(1, 2);
        repo.favorites.add(2);
        FakeSender sender = new FakeSender();
        service(repo, sender).handle(closed(8));

        assertEquals(List.of(1002L), sender.sentTo);
        assertTrue(sender.textTo.get(1002L).contains("закрыта"));
    }

    @Test
    void redeliveryKeepsEachRecipientsVariant() {
        FakeRepo repo = new FakeRepo(1, 2);
        repo.favorites.add(1);
        FakeSender sender = new FakeSender().failing(1002,
                new TemporaryException("down"), new TemporaryException("down"), new TemporaryException("down"));
        NotificationService service = service(repo, sender);

        service.handle(updated(7));                // у 2 не вышло, 1 получил «изменилась»
        service.handle(updated(7));                // повторная доставка из очереди

        assertTrue(sender.textTo.get(1001L).contains("Что изменилось"));
        assertTrue(sender.textTo.get(1002L).contains("Новая вакансия"));
        assertEquals(1L, sender.count(1001));      // первому повторно не слали
    }

    // ---- надёжность ----

    @Test
    void sendsToEveryRecipientAndMarksSent() {
        FakeRepo repo = new FakeRepo(1, 2, 3);
        FakeSender sender = new FakeSender();
        HandleResult result = service(repo, sender).handle(created(1));

        assertEquals(3, result.sent());
        assertEquals(List.of(1001L, 1002L, 1003L), sender.sentTo);
        assertEquals(Map.of(1, "sent", 2, "sent", 3, "sent"), repo.statusByUser(1));
        assertTrue(sender.textTo.get(1001L).contains("Python-разработчик"));
    }

    @Test
    void repeatedEventDoesNotSendDuplicates() {
        FakeRepo repo = new FakeRepo(1, 2);
        FakeSender sender = new FakeSender();
        NotificationService service = service(repo, sender);
        service.handle(created(1));
        HandleResult again = service.handle(created(1));   // то же событие пришло второй раз

        assertEquals(2, sender.sentTo.size());
        assertEquals(0, again.sent());
    }

    @Test
    void sameChangeIdWithDifferentChangedAtIsADifferentChange() {
        // ключ журнала включает changed_at (в том числе при переходе в партицию следующего года)
        FakeRepo repo = new FakeRepo(1);
        FakeSender sender = new FakeSender();
        NotificationService service = service(repo, sender);
        service.handle(created(1));
        service.handle(new ChangeEvent(1, VID, ChangeType.CREATED, T0.plusMonths(2), Map.of()));
        assertEquals(2, sender.sentTo.size());
    }

    @Test
    void blockedUserIsMarkedAndOthersStillGetMessage() {
        FakeRepo repo = new FakeRepo(1, 2);
        FakeSender sender = new FakeSender().failing(1001, new BotBlockedException("blocked by user"));
        HandleResult result = service(repo, sender).handle(created(1));

        assertEquals(1, result.sent());
        assertEquals(1, result.blocked());
        assertEquals(Map.of(1, "blocked", 2, "sent"), repo.statusByUser(1));
        assertTrue(repo.blockedUsers.contains(1));
        assertEquals(1L, sender.count(1001));   // заблокированного не повторяем
    }

    @Test
    void temporaryFailureIsRetriedAndThenSucceeds() {
        FakeRepo repo = new FakeRepo(1);
        FakeSender sender = new FakeSender().failing(1001, new TemporaryException("503"), new TemporaryException("503"));
        HandleResult result = service(repo, sender).handle(created(1));

        assertEquals(1, result.sent());
        assertEquals(3L, sender.count(1001));
    }

    @Test
    void exhaustedRetriesLeaveNotificationPendingForRedelivery() {
        FakeRepo repo = new FakeRepo(1, 2);
        FakeSender sender = new FakeSender().failing(1001,
                new TemporaryException("down"), new TemporaryException("down"), new TemporaryException("down"));
        NotificationService service = service(repo, sender);

        HandleResult first = service.handle(created(1));
        assertEquals(1, first.sent());
        assertEquals(1, first.retryLater());
        assertEquals(Map.of(1, "pending", 2, "sent"), repo.statusByUser(1));

        HandleResult second = service.handle(created(1));   // повторная доставка сообщения из очереди
        assertEquals(1, second.sent());
        assertEquals(0, second.retryLater());
        assertEquals(Map.of(1, "sent", 2, "sent"), repo.statusByUser(1));
        assertEquals(1L, sender.count(1002));                // второму ничего не слали повторно
    }

    @Test
    void permanentErrorMarksFailedWithoutRetry() {
        FakeRepo repo = new FakeRepo(1);
        FakeSender sender = new FakeSender().failing(1001, new PermanentException("can't parse entities"));
        HandleResult result = service(repo, sender).handle(created(1));

        assertEquals(1, result.failed());
        assertEquals(Map.of(1, "failed"), repo.statusByUser(1));
        assertEquals(1L, sender.count(1001));
    }

    @Test
    void rateLimiterIsUsedBeforeEveryAttempt() {
        FakeRepo repo = new FakeRepo(1);
        FakeLimiter limiter = new FakeLimiter();
        FakeSender sender = new FakeSender().failing(1001, new TemporaryException("x"));
        service(repo, sender, limiter).handle(created(1));
        assertEquals(List.of(1001L, 1001L), limiter.calls);   // попытка + повтор
    }

    @Test
    void missingVacancySendsNothing() {
        FakeRepo repo = new FakeRepo(1);
        repo.vacancy = null;
        FakeSender sender = new FakeSender();
        HandleResult result = service(repo, sender).handle(created(1));
        assertTrue(sender.calls.isEmpty());
        assertEquals(0, result.sent());
    }

    @Test
    void noRecipientsSendsNothing() {
        FakeRepo repo = new FakeRepo();
        FakeSender sender = new FakeSender();
        HandleResult result = service(repo, sender).handle(created(1));
        assertTrue(sender.calls.isEmpty());
        assertEquals(0, result.sent());
        assertTrue(repo.status.isEmpty());
    }

    @Test
    void unexpectedErrorPropagatesForRedelivery() {
        FakeRepo repo = new FakeRepo(1) {
            @Override
            public void markSent(Recipient r) {
                throw new IllegalStateException("db down");
            }
        };
        assertThrows(IllegalStateException.class, () -> service(repo, new FakeSender()).handle(created(1)));
    }

    @Test
    void giveUpMarksRemainingPendingAsFailed() {
        FakeRepo repo = new FakeRepo(1, 2);
        FakeSender sender = new FakeSender().failing(1001,
                new TemporaryException("down"), new TemporaryException("down"), new TemporaryException("down"));
        NotificationService service = service(repo, sender);
        service.handle(created(1));

        assertEquals(1, service.giveUp(created(1)));
        assertEquals(Map.of(1, "failed", 2, "sent"), repo.statusByUser(1));
    }

    @Test
    void concurrencyIsLimitedBySemaphoreAndEveryoneIsStillServed() {
        // потокобезопасная заглушка журнала + настоящие виртуальные потоки
        FakeRepo repo = new FakeRepo(1, 2, 3, 4, 5, 6, 7, 8) {
            @Override
            public synchronized void markSent(Recipient r) {
                super.markSent(r);
            }
        };
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        AtomicInteger delivered = new AtomicInteger();
        TelegramSender slowSender = (chatId, text) -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            running.decrementAndGet();
            delivered.incrementAndGet();
        };
        ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor();
        NotificationService service = new NotificationService(repo, slowSender, chatId -> { },
                new RetryExecutor(3, 1.0, 30.0, seconds -> { }, d -> d), virtual, 2, true);

        HandleResult result = service.handle(created(1));

        assertEquals(8, result.sent());
        assertEquals(8, delivered.get());
        assertTrue(maxRunning.get() <= 2, "одновременно отправлялось " + maxRunning.get());
        virtual.shutdown();
    }
}
