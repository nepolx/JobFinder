package ru.vacancy.notifier.mq;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.vacancy.notifier.model.ChangeEvent;
import ru.vacancy.notifier.model.InvalidEventException;
import ru.vacancy.notifier.service.EventHandler;
import ru.vacancy.notifier.service.HandleResult;

/**
 * Решает, что сделать с сообщением из RabbitMQ. Не зависит от Spring AMQP, поэтому
 * тестируется обычными unit-тестами.
 *
 * Схема повторов: nack без requeue -> dead-letter в vacancy.events.retry -> через TTL
 * сообщение возвращается в vacancy.events. Число кругов считаем по заголовку x-death.
 */
public final class EventMessageProcessor {

    public enum Decision { ACK, NACK_NO_REQUEUE }

    private static final Logger log = LoggerFactory.getLogger(EventMessageProcessor.class);

    private final EventParser parser;
    private final EventHandler handler;
    private final int maxRedeliveries;

    public EventMessageProcessor(EventParser parser, EventHandler handler, int maxRedeliveries) {
        this.parser = parser;
        this.handler = handler;
        this.maxRedeliveries = maxRedeliveries;
    }

    public Decision process(byte[] body, Map<String, Object> headers) {
        ChangeEvent event;
        try {
            event = parser.parse(body);
        } catch (InvalidEventException e) {
            // Повторять бессмысленно, а в очереди оставлять нельзя.
            log.error("отбрасываем некорректное событие: {}; body={}", e.getMessage(), preview(body));
            return Decision.ACK;
        }

        HandleResult result = null;
        try {
            result = handler.handle(event);
        } catch (Exception e) {
            log.error("change " + event.changeId() + ": ошибка обработки", e);
        }

        if (result != null && result.retryLater() == 0) {
            return Decision.ACK;
        }

        if (redeliveryCount(headers) >= maxRedeliveries) {
            try {
                handler.giveUp(event);
            } catch (Exception e) {
                log.error("change " + event.changeId() + ": не удалось пометить как failed", e);
                return Decision.NACK_NO_REQUEUE;
            }
            return Decision.ACK;
        }
        return Decision.NACK_NO_REQUEUE; // уйдёт в retry-очередь и вернётся через TTL
    }

    /** Сколько раз сообщение уже прошло круг через retry-очередь (максимум по записям x-death). */
    public static int redeliveryCount(Map<String, Object> headers) {
        if (headers == null || !(headers.get("x-death") instanceof List<?> deaths)) {
            return 0;
        }
        int max = 0;
        for (Object death : deaths) {
            if (death instanceof Map<?, ?> d && d.get("count") instanceof Number n) {
                max = Math.max(max, n.intValue());
            }
        }
        return max;
    }

    private static String preview(byte[] body) {
        String s = new String(body, StandardCharsets.UTF_8);
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
