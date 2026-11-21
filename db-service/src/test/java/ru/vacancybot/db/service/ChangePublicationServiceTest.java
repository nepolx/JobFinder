package ru.vacancybot.db.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import ru.vacancybot.common.dto.ChangeKeyDto;
import ru.vacancybot.common.dto.PublishFailedDto;
import ru.vacancybot.db.repository.VacancyChangeRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePublicationServiceTest {

    private static final Instant T = Instant.parse("2026-11-08T12:00:00Z");

    @Mock VacancyChangeRepository repository;
    ChangePublicationService service;

    @BeforeEach
    void setUp() {
        service = new ChangePublicationService(repository, 5);
    }

    private Pageable requestedPage(Integer limit) {
        when(repository.findByPublishedAtIsNullAndPublishAttemptsLessThanOrderByIdAsc(anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        service.unpublished(limit);
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByPublishedAtIsNullAndPublishAttemptsLessThanOrderByIdAsc(eq(5), captor.capture());
        return captor.getValue();
    }

    @Test
    void unpublished_defaultLimitIs200() {
        assertThat(requestedPage(null).getPageSize()).isEqualTo(200);
    }

    @Test
    void unpublished_limitIsCappedAt1000() {
        assertThat(requestedPage(5000).getPageSize()).isEqualTo(1000);
    }

    @Test
    void unpublished_customLimitIsUsed() {
        assertThat(requestedPage(10).getPageSize()).isEqualTo(10);
    }

    @Test
    void unpublished_zeroOrNegativeLimit_isRejected() {
        assertThatThrownBy(() -> service.unpublished(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.unpublished(-5)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void markPublished_sumsUpdatedRows() {
        when(repository.markPublished(eq(1L), eq(T), any())).thenReturn(1);
        when(repository.markPublished(eq(2L), eq(T), any())).thenReturn(0); // уже помечено

        int marked = service.markPublished(List.of(new ChangeKeyDto(1L, T), new ChangeKeyDto(2L, T)));

        assertThat(marked).isEqualTo(1);
    }

    @Test
    void markPublished_emptyList_isZero() {
        assertThat(service.markPublished(List.of())).isZero();
        assertThat(service.markPublished(null)).isZero();
        verifyNoInteractions(repository);
    }

    @Test
    void markPublished_moreThan1000Keys_isRejected() {
        List<ChangeKeyDto> keys = new ArrayList<>();
        for (long i = 0; i < 1001; i++) {
            keys.add(new ChangeKeyDto(i, T));
        }
        assertThatThrownBy(() -> service.markPublished(keys)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markPublished_keyWithoutChangedAt_isRejected() {
        assertThatThrownBy(() -> service.markPublished(List.of(new ChangeKeyDto(1L, null))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishFailed_truncatesLongError_to500() {
        when(repository.registerPublishFailure(eq(1L), eq(T), any())).thenReturn(1);
        String longError = "x".repeat(900);

        int updated = service.publishFailed(new PublishFailedDto(1L, T, longError));

        assertThat(updated).isEqualTo(1);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(repository).registerPublishFailure(eq(1L), eq(T), captor.capture());
        assertThat(captor.getValue()).hasSize(500);
    }

    @Test
    void publishFailed_withoutKey_isRejected() {
        assertThatThrownBy(() -> service.publishFailed(new PublishFailedDto(null, T, "err")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.publishFailed(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
