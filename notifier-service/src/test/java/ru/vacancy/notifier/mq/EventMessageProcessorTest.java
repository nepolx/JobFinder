package ru.vacancy.notifier.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.vacancy.notifier.model.ChangeEvent;
import ru.vacancy.notifier.model.ChangeType;
import ru.vacancy.notifier.model.InvalidEventException;
import ru.vacancy.notifier.mq.EventMessageProcessor.Decision;
import ru.vacancy.notifier.service.EventHandler;
import ru.vacancy.notifier.service.HandleResult;

class EventMessageProcessorTest {

    static final ChangeEvent EVENT = new ChangeEvent(1, UUID.randomUUID(), ChangeType.CREATED,
            OffsetDateTime.parse("2026-11-08T12:00:00Z"), Map.of());
    static final byte[] BODY = "ok".getBytes(StandardCharsets.UTF_8);

    static class FakeHandler implements EventHandler {
        HandleResult result = new HandleResult(1, 0, 0, 0);
        RuntimeException error;
        int handled;
        int gaveUp;

        @Override
        public HandleResult handle(ChangeEvent event) {
            handled++;
            if (error != null) {
                throw error;
            }
            return result;
        }

        @Override
        public int giveUp(ChangeEvent event) {
            gaveUp++;
            return 1;
        }
    }

    /** Парсер, который принимает только тело "ok". */
    static final EventParser PARSER = body -> {
        if (!new String(body, StandardCharsets.UTF_8).equals("ok")) {
            throw new InvalidEventException("garbage");
        }
        return EVENT;
    };

    static Map<String, Object> deaths(int count) {
        return Map.of("x-death", List.of(Map.of("queue", "vacancy.events", "reason", "rejected", "count", (long) count)));
    }

    // ---- число кругов повтора ----

    @Test
    void redeliveryCountReadsXDeathHeader() {
        assertEquals(0, EventMessageProcessor.redeliveryCount(null));
        assertEquals(0, EventMessageProcessor.redeliveryCount(Map.of()));
        assertEquals(3, EventMessageProcessor.redeliveryCount(deaths(3)));
    }

    @Test
    void redeliveryCountTakesMaxOverQueues() {
        Map<String, Object> headers = Map.of("x-death", List.of(Map.of("count", 2L), Map.of("count", 3L)));
        assertEquals(3, EventMessageProcessor.redeliveryCount(headers));
    }

    @Test
    void redeliveryCountIgnoresMalformedHeader() {
        assertEquals(0, EventMessageProcessor.redeliveryCount(Map.of("x-death", "garbage")));
    }

    // ---- решения ----

    @Test
    void acksWhenEverythingDelivered() {
        FakeHandler handler = new FakeHandler();
        assertEquals(Decision.ACK, new EventMessageProcessor(PARSER, handler, 5).process(BODY, Map.of()));
    }

    @Test
    void nacksWithoutRequeueWhenRetryNeeded() {
        FakeHandler handler = new FakeHandler();
        handler.result = new HandleResult(1, 0, 0, 2);
        assertEquals(Decision.NACK_NO_REQUEUE, new EventMessageProcessor(PARSER, handler, 5).process(BODY, Map.of()));
        assertEquals(0, handler.gaveUp);
    }

    @Test
    void nacksWhenHandlerCrashes() {
        FakeHandler handler = new FakeHandler();
        handler.error = new IllegalStateException("db down");
        assertEquals(Decision.NACK_NO_REQUEUE, new EventMessageProcessor(PARSER, handler, 5).process(BODY, Map.of()));
    }

    @Test
    void givesUpAfterTooManyRedeliveries() {
        FakeHandler handler = new FakeHandler();
        handler.result = new HandleResult(0, 0, 0, 1);
        Decision decision = new EventMessageProcessor(PARSER, handler, 5).process(BODY, deaths(5));
        assertEquals(Decision.ACK, decision);
        assertEquals(1, handler.gaveUp);
    }

    @Test
    void givesUpAlsoWhenHandlerKeepsCrashing() {
        FakeHandler handler = new FakeHandler();
        handler.error = new IllegalStateException("db down");
        assertEquals(Decision.ACK, new EventMessageProcessor(PARSER, handler, 5).process(BODY, deaths(5)));
        assertEquals(1, handler.gaveUp);
    }

    @Test
    void nacksWhenGivingUpItselfFails() {
        FakeHandler handler = new FakeHandler() {
            @Override
            public int giveUp(ChangeEvent event) {
                throw new IllegalStateException("db down");
            }
        };
        handler.result = new HandleResult(0, 0, 0, 1);
        assertEquals(Decision.NACK_NO_REQUEUE,
                new EventMessageProcessor(PARSER, handler, 5).process(BODY, deaths(5)));
    }

    @Test
    void invalidEventIsAckedAndNeverHandled() {
        FakeHandler handler = new FakeHandler();
        Decision decision = new EventMessageProcessor(PARSER, handler, 5)
                .process("garbage".getBytes(StandardCharsets.UTF_8), Map.of());
        assertEquals(Decision.ACK, decision);
        assertEquals(0, handler.handled);
    }
}
