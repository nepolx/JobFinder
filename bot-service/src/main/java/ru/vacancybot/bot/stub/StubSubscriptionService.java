package ru.vacancybot.bot.stub;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.vacancybot.bot.domain.Subscription;
import ru.vacancybot.bot.domain.SubscriptionCriteria;
import ru.vacancybot.bot.port.SubscriptionService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Заглушка: подписки в памяти. Заменяется реальным сервисом напарника. */
@Service
@ConditionalOnProperty(name = "bot.stubs.enabled", havingValue = "true", matchIfMissing = true)
public class StubSubscriptionService implements SubscriptionService {

    private final Map<Long, Map<Long, Subscription>> byUser = new LinkedHashMap<>();
    private long seq = 0;

    @Override
    public synchronized Subscription create(long telegramId, SubscriptionCriteria criteria) {
        Subscription s = new Subscription(++seq, criteria, true);
        byUser.computeIfAbsent(telegramId, k -> new LinkedHashMap<>()).put(s.id(), s);
        return s;
    }

    @Override
    public synchronized Subscription update(long telegramId, long subscriptionId, SubscriptionCriteria criteria) {
        Subscription old = require(telegramId, subscriptionId);
        Subscription updated = new Subscription(old.id(), criteria, old.active());
        byUser.get(telegramId).put(old.id(), updated);
        return updated;
    }

    @Override
    public synchronized List<Subscription> list(long telegramId) {
        return new ArrayList<>(byUser.getOrDefault(telegramId, Map.of()).values());
    }

    @Override
    public synchronized Optional<Subscription> find(long telegramId, long subscriptionId) {
        return Optional.ofNullable(byUser.getOrDefault(telegramId, Map.of()).get(subscriptionId));
    }

    @Override
    public synchronized void setActive(long telegramId, long subscriptionId, boolean active) {
        Subscription old = require(telegramId, subscriptionId);
        byUser.get(telegramId).put(old.id(), new Subscription(old.id(), old.criteria(), active));
    }

    @Override
    public synchronized void delete(long telegramId, long subscriptionId) {
        Map<Long, Subscription> subs = byUser.get(telegramId);
        if (subs != null) {
            subs.remove(subscriptionId);
        }
    }

    private Subscription require(long telegramId, long subscriptionId) {
        return find(telegramId, subscriptionId)
                .orElseThrow(() -> new NoSuchElementException("Subscription " + subscriptionId + " not found"));
    }
}
