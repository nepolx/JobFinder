package ru.vacancybot.bot.client;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.vacancybot.bot.domain.Subscription;
import ru.vacancybot.bot.domain.SubscriptionCriteria;
import ru.vacancybot.bot.port.SubscriptionService;
import ru.vacancybot.common.dto.SubscriptionDto;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/** Подписки через REST db-service (см. API.md, часть A). */
@Service
@ConditionalOnProperty(name = "bot.stubs.enabled", havingValue = "false")
public class HttpSubscriptionService implements SubscriptionService {

    private static final TypeReference<SubscriptionDto> ONE = new TypeReference<>() { };
    private static final TypeReference<List<SubscriptionDto>> MANY = new TypeReference<>() { };

    private final DbServiceClient db;

    public HttpSubscriptionService(DbServiceClient db) {
        this.db = db;
    }

    @Override
    public Subscription create(long telegramId, SubscriptionCriteria criteria) {
        SubscriptionDto created = db.call("POST", "/api/subscriptions", toDto(telegramId, null, criteria, true), ONE);
        return fromDto(created);
    }

    @Override
    public Subscription update(long telegramId, long subscriptionId, SubscriptionCriteria criteria) {
        // active в теле не влияет: db-service при update его не меняет
        SubscriptionDto body = toDto(telegramId, subscriptionId, criteria, true);
        return fromDto(db.callOptional("PUT", path(telegramId, subscriptionId), body, ONE)
                .orElseThrow(() -> new NoSuchElementException("Subscription " + subscriptionId + " not found")));
    }

    @Override
    public List<Subscription> list(long telegramId) {
        return db.call("GET", "/api/users/" + telegramId + "/subscriptions", null, MANY)
                .stream().map(HttpSubscriptionService::fromDto).toList();
    }

    @Override
    public Optional<Subscription> find(long telegramId, long subscriptionId) {
        try {
            return db.callOptional("GET", path(telegramId, subscriptionId), null, ONE).map(HttpSubscriptionService::fromDto);
        } catch (DbServiceException e) {
            if (e.status() == 405) {
                // старая версия db-service без GET одной подписки — ищем в списке
                return list(telegramId).stream().filter(s -> s.id() == subscriptionId).findFirst();
            }
            throw e;
        }
    }

    @Override
    public void setActive(long telegramId, long subscriptionId, boolean active) {
        db.call("PATCH", path(telegramId, subscriptionId) + "/active?value=" + active, null);
    }

    @Override
    public void delete(long telegramId, long subscriptionId) {
        db.call("DELETE", path(telegramId, subscriptionId), null);
    }

    private static String path(long telegramId, long subscriptionId) {
        return "/api/users/" + telegramId + "/subscriptions/" + subscriptionId;
    }

    static SubscriptionDto toDto(long telegramId, Long id, SubscriptionCriteria c, boolean active) {
        return new SubscriptionDto(id, telegramId, c.title(), c.regionCode(), c.salaryMin(),
                c.experienceCode(), c.scheduleCode(), c.employmentCode(), c.keywords(), active);
    }

    static Subscription fromDto(SubscriptionDto d) {
        SubscriptionCriteria criteria = new SubscriptionCriteria(
                blankToNull(d.title()), blankToNull(d.region()),
                d.salaryMin() == null || d.salaryMin() == 0 ? null : d.salaryMin(),
                blankToNull(d.experience()), blankToNull(d.schedule()), blankToNull(d.employment()),
                blankToNull(d.keywords()));
        return new Subscription(d.id(), criteria, d.active());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
