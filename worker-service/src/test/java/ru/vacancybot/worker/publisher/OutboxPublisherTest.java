package ru.vacancybot.worker.publisher;

import org.junit.jupiter.api.Test;
import ru.vacancybot.worker.client.dbservice.DbServiceClient;
import ru.vacancybot.worker.config.EventsProperties;
import ru.vacancybot.common.dto.ChangeKeyDto;
import ru.vacancybot.common.dto.VacancyChangeDto;
import ru.vacancybot.worker.exception.DbServiceException;
import ru.vacancybot.worker.publisher.OutboxPublisher.DrainResult;
import ru.vacancybot.worker.publisher.VacancyEventPublisher.PublishOutcome;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    private static final Instant AT = Instant.parse("2026-11-09T12:30:00Z");

    private final DbServiceClient db = mock(DbServiceClient.class);
    private final VacancyEventPublisher publisher = mock(VacancyEventPublisher.class);

    private OutboxPublisher outbox(int batchSize) {
        return new OutboxPublisher(db, publisher,
                new EventsProperties("vacancy.events", batchSize, Duration.ofSeconds(1)));
    }

    private static VacancyChangeDto change(long id) {
        return new VacancyChangeDto(id, AT, "v" + id, "created", null);
    }

    private static ChangeKeyDto key(long id) {
        return new ChangeKeyDto(id, AT);
    }

    private void publisherConfirmsEverything() {
        when(publisher.publish(anyList())).thenAnswer(invocation -> {
            List<VacancyChangeDto> changes = invocation.getArgument(0);
            return new PublishOutcome(changes.stream().map(ChangeKeyDto::of).toList(), Map.of(), null);
        });
    }

    @Test
    void emptyOutboxDoesNothing() {
        when(db.fetchUnpublished(200)).thenReturn(List.of());

        DrainResult result = outbox(200).drain();

        assertThat(result).isEqualTo(new DrainResult(0, 0, false));
        verify(publisher, never()).publish(anyList());
        verify(db, never()).markPublished(anyList());
    }

    @Test
    void confirmedEventsAreMarkedPublished() {
        when(db.fetchUnpublished(200)).thenReturn(List.of(change(1), change(2)));
        publisherConfirmsEverything();

        DrainResult result = outbox(200).drain();

        assertThat(result).isEqualTo(new DrainResult(2, 0, false));
        verify(db).markPublished(List.of(key(1), key(2)));
        verify(db, never()).markPublishFailed(any(ChangeKeyDto.class), anyString());
        verify(db, times(1)).fetchUnpublished(200);
    }

    @Test
    void fullBatchMeansThereMayBeMoreSoItContinuesUntilShortBatch() {
        when(db.fetchUnpublished(2)).thenReturn(List.of(change(1), change(2)), List.of(change(3)));
        publisherConfirmsEverything();

        DrainResult result = outbox(2).drain();

        assertThat(result.published()).isEqualTo(3);
        verify(db, times(2)).fetchUnpublished(2);
        verify(db).markPublished(List.of(key(1), key(2)));
        verify(db).markPublished(List.of(key(3)));
    }

    @Test
    void brokerProblemStopsDrainingAndDoesNotCountAttempts() {
        when(db.fetchUnpublished(200)).thenReturn(List.of(change(1), change(2), change(3)));
        when(publisher.publish(anyList())).thenReturn(
                new PublishOutcome(List.of(key(1)), Map.of(), "send failed: connection lost"));

        DrainResult result = outbox(200).drain();

        assertThat(result).isEqualTo(new DrainResult(1, 0, true));
        verify(db).markPublished(List.of(key(1)));
        verify(db, never()).markPublishFailed(any(ChangeKeyDto.class), anyString());
        verify(db, times(1)).fetchUnpublished(200);
    }

    @Test
    void brokerProblemWithNothingConfirmedMarksNothing() {
        when(db.fetchUnpublished(200)).thenReturn(List.of(change(1)));
        when(publisher.publish(anyList())).thenReturn(
                new PublishOutcome(List.of(), Map.of(), "message is unroutable"));

        DrainResult result = outbox(200).drain();

        assertThat(result.brokerFailed()).isTrue();
        verify(db, never()).markPublished(anyList());
    }

    @Test
    void brokenDataIsReportedAsFailedAttemptAndLoopStops() {
        when(db.fetchUnpublished(2)).thenReturn(List.of(change(1), change(2)));
        when(publisher.publish(anyList())).thenReturn(
                new PublishOutcome(List.of(key(1)), Map.of(key(2), "Cannot build event: changedAt is missing"), null));

        DrainResult result = outbox(2).drain();

        assertThat(result).isEqualTo(new DrainResult(1, 1, false));
        verify(db).markPublished(List.of(key(1)));
        verify(db).markPublishFailed(key(2), "Cannot build event: changedAt is missing");
        verify(db, times(1)).fetchUnpublished(2);
    }

    @Test
    void dbServiceErrorIsNotSwallowed() {
        when(db.fetchUnpublished(200)).thenThrow(new DbServiceException("db-service is down", 503, true));

        assertThatThrownBy(() -> outbox(200).drain()).isInstanceOf(DbServiceException.class);
    }

    @Test
    void failureToMarkAfterPublishingPropagatesSoEventsAreRepublishedLater() {
        when(db.fetchUnpublished(200)).thenReturn(List.of(change(1)));
        publisherConfirmsEverything();
        when(db.markPublished(anyList())).thenThrow(new DbServiceException("db-service is down", 503, true));

        assertThatThrownBy(() -> outbox(200).drain()).isInstanceOf(DbServiceException.class);
    }
}
