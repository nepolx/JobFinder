package ru.vacancybot.worker.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.vacancybot.worker.config.EventsProperties;
import ru.vacancybot.worker.dto.dbservice.ChangeKey;
import ru.vacancybot.worker.dto.dbservice.VacancyChangeDto;
import ru.vacancybot.worker.dto.event.VacancyEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class VacancyEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final EventsProperties properties;

    public VacancyEventPublisher(RabbitTemplate rabbitTemplate,
                                 ObjectMapper objectMapper,
                                 EventsProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public record PublishOutcome(List<ChangeKey> confirmed,
                                 Map<ChangeKey, String> rejected,
                                 String brokerError) {
    }

    private record Sent(ChangeKey key, CorrelationData correlation) {
    }

    public PublishOutcome publish(List<VacancyChangeDto> changes) {
        List<ChangeKey> confirmed = new ArrayList<>();
        Map<ChangeKey, String> rejected = new LinkedHashMap<>();
        List<Sent> sent = new ArrayList<>();
        String brokerError = null;

        // 1. отправляем всё, не дожидаясь подтверждений по одному
        for (VacancyChangeDto change : changes) {
            ChangeKey key = ChangeKey.of(change);

            VacancyEvent event;
            byte[] body;
            try {
                event = VacancyEvent.from(change);
                body = objectMapper.writeValueAsBytes(event);
            } catch (IllegalArgumentException | JsonProcessingException e) {
                log.error("Cannot build event for change {}: {}", key, e.getMessage());
                rejected.put(key, "Cannot build event: " + e.getMessage());
                continue;
            }

            String messageId = event.changeId() + "|" + event.changedAt();
            Message message = MessageBuilder.withBody(body)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("UTF-8")
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setMessageId(messageId)
                    .build();
            CorrelationData correlation = new CorrelationData(messageId);

            try {
                // default exchange: routing key равен имени очереди
                rabbitTemplate.send("", properties.queue(), message, correlation);
            } catch (AmqpException e) {
                brokerError = "send failed: " + e.getMessage();
                break;
            }
            sent.add(new Sent(key, correlation));
        }

        // 2. ждём подтверждений; общий срок на пачку ограничен
        long deadline = System.nanoTime() + properties.confirmTimeout().toNanos();
        for (Sent item : sent) {
            String problem = awaitConfirm(item.correlation(), deadline);
            if (problem == null) {
                confirmed.add(item.key());
            } else if (brokerError == null) {
                brokerError = problem;
            }
        }

        return new PublishOutcome(confirmed, rejected, brokerError);
    }

    private String awaitConfirm(CorrelationData correlation, long deadlineNanos) {
        long remaining = Math.max(0, deadlineNanos - System.nanoTime());
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(remaining, TimeUnit.NANOSECONDS);
            if (!confirm.isAck()) {
                return "broker nack: " + confirm.getReason();
            }
            // подтверждение приходит и для недоставляемых сообщений, но после возврата
            if (correlation.getReturned() != null) {
                return "message is unroutable (does the queue '" + properties.queue() + "' exist?): "
                        + correlation.getReturned().getReplyText();
            }
            return null;
        } catch (TimeoutException e) {
            return "no confirm from broker within " + properties.confirmTimeout();
        } catch (ExecutionException e) {
            return "confirm failed: " + e.getCause();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted while waiting for confirm";
        }
    }
}