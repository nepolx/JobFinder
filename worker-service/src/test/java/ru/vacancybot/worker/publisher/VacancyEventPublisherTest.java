package ru.vacancybot.worker.publisher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import ru.vacancybot.worker.config.EventsProperties;
import ru.vacancybot.worker.dto.dbservice.ChangeKeyDto;
import ru.vacancybot.worker.dto.dbservice.VacancyChangeDto;
import ru.vacancybot.worker.publisher.VacancyEventPublisher.PublishOutcome;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class VacancyEventPublisherTest {

    private static final Instant AT = Instant.parse("2026-11-09T12:30:00.123456Z");

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final EventsProperties properties =
            new EventsProperties("vacancy.events", 200, Duration.ofMillis(200));
    private final VacancyEventPublisher publisher = new VacancyEventPublisher(rabbitTemplate, mapper, properties);

    private static VacancyChangeDto created(long id) {
        return new VacancyChangeDto(id, AT, "3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d", "created", null);
    }

    private static VacancyChangeDto updated(long id) {
        return new VacancyChangeDto(id, AT, "3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d", "updated",
                Map.of("salary_min", List.of(50000, 70000)));
    }

    private void brokerAcksEverything() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void confirmedEventsAreReturnedForMarkingAsPublished() {
        brokerAcksEverything();

        PublishOutcome outcome = publisher.publish(List.of(created(1), updated(2)));

        assertThat(outcome.confirmed()).containsExactly(new ChangeKeyDto(1L, AT), new ChangeKeyDto(2L, AT));
        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.brokerError()).isNull();
    }

    @Test
    void sendsToDefaultExchangeWithQueueNameAsRoutingKey() {
        brokerAcksEverything();

        publisher.publish(List.of(created(1)));

        verify(rabbitTemplate).send(eq(""), eq("vacancy.events"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void messageIsPersistentJsonWithoutJavaTypeHeader() {
        brokerAcksEverything();
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);

        publisher.publish(List.of(created(123)));

        verify(rabbitTemplate).send(anyString(), anyString(), captor.capture(), any(CorrelationData.class));
        MessageProperties properties = captor.getValue().getMessageProperties();
        assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
        assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getMessageId()).isEqualTo("123|2026-11-09T12:30:00.123456Z");
        assertThat(properties.getHeaders()).doesNotContainKey("__TypeId__");
    }

    @Test
    void createdEventBodyMatchesContract() throws IOException {
        brokerAcksEverything();
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);

        publisher.publish(List.of(created(123)));

        verify(rabbitTemplate).send(anyString(), anyString(), captor.capture(), any(CorrelationData.class));
        JsonNode json = mapper.readTree(captor.getValue().getBody());
        assertThat(json.get("change_id").asLong()).isEqualTo(123);
        assertThat(json.get("changed_at").asText()).isEqualTo("2026-11-09T12:30:00.123456Z");
        assertThat(json.get("vacancy_id").asText()).isEqualTo("3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d");
        assertThat(json.get("type").asText()).isEqualTo("created");
        assertThat(json.has("diff")).isFalse();
    }

    @Test
    void updatedEventBodyCarriesDiff() throws IOException {
        brokerAcksEverything();
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);

        publisher.publish(List.of(updated(124)));

        verify(rabbitTemplate).send(anyString(), anyString(), captor.capture(), any(CorrelationData.class));
        JsonNode json = mapper.readTree(captor.getValue().getBody());
        assertThat(json.get("type").asText()).isEqualTo("updated");
        assertThat(json.get("diff").get("salary_min").get(0).asInt()).isEqualTo(50000);
        assertThat(json.get("diff").get("salary_min").get(1).asInt()).isEqualTo(70000);
    }

    @Test
    void emptyBatchSendsNothing() {
        PublishOutcome outcome = publisher.publish(List.of());

        assertThat(outcome.confirmed()).isEmpty();
        assertThat(outcome.brokerError()).isNull();
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void nackMeansNotPublishedAndIsABrokerProblem() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "internal error"));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        PublishOutcome outcome = publisher.publish(List.of(created(1)));

        assertThat(outcome.confirmed()).isEmpty();
        assertThat(outcome.rejected()).isEmpty();
        assertThat(outcome.brokerError()).contains("nack").contains("internal error");
    }

    @Test
    void returnedMessageMeansQueueIsMissingEvenIfBrokerAcked() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(new Message(new byte[0]), 312, "NO_ROUTE", "", "vacancy.events"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        PublishOutcome outcome = publisher.publish(List.of(created(1)));

        assertThat(outcome.confirmed()).isEmpty();
        assertThat(outcome.brokerError()).contains("unroutable").contains("vacancy.events");
    }

    @Test
    void missingConfirmIsReportedAfterTimeout() {
        PublishOutcome outcome = publisher.publish(List.of(created(1)));

        assertThat(outcome.confirmed()).isEmpty();
        assertThat(outcome.brokerError()).contains("no confirm");
    }

    @Test
    void sendFailureStopsSendingAndKeepsAlreadyConfirmedEvents() {
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() > 1) {
                throw new AmqpException("connection lost");
            }
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        PublishOutcome outcome = publisher.publish(List.of(created(1), created(2), created(3)));

        assertThat(outcome.confirmed()).containsExactly(new ChangeKeyDto(1L, AT));
        assertThat(outcome.brokerError()).contains("send failed").contains("connection lost");
        verify(rabbitTemplate, times(2)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void changeThatCannotBecomeAnEventIsRejectedAndOthersStillGo() {
        brokerAcksEverything();
        VacancyChangeDto broken = new VacancyChangeDto(2L, null, "v", "created", null);   // нет changedAt

        PublishOutcome outcome = publisher.publish(List.of(created(1), broken, updated(3)));

        assertThat(outcome.confirmed()).containsExactly(new ChangeKeyDto(1L, AT), new ChangeKeyDto(3L, AT));
        assertThat(outcome.rejected()).hasSize(1);
        assertThat(outcome.rejected().values().iterator().next()).contains("changedAt");
        assertThat(outcome.brokerError()).isNull();
        verify(rabbitTemplate, times(2)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void unsupportedChangeTypeIsRejected() {
        brokerAcksEverything();
        VacancyChangeDto closed = new VacancyChangeDto(5L, AT, "v", "closed", null);

        PublishOutcome outcome = publisher.publish(List.of(closed));

        assertThat(outcome.confirmed()).isEmpty();
        assertThat(outcome.rejected()).hasSize(1);
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }
}
