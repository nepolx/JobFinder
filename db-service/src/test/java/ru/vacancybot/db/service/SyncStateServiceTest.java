package ru.vacancybot.db.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.vacancybot.common.dto.SyncStateDto;
import ru.vacancybot.db.entity.SyncState;
import ru.vacancybot.db.exception.NotFoundException;
import ru.vacancybot.db.repository.SyncStateRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncStateServiceTest {

    @Mock SyncStateRepository repository;
    SyncStateService service;

    @BeforeEach
    void setUp() {
        service = new SyncStateService(repository);
    }

    @Test
    void get_missingKey_isNotFound() {
        when(repository.findById("vacancies.cursor")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("vacancies.cursor")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void put_createsNewValue() {
        when(repository.findById("vacancies.cursor")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(SyncState.class))).thenAnswer(i -> i.getArgument(0));

        SyncStateDto dto = service.put("vacancies.cursor", "2026-11-08T09:30:00Z");

        assertThat(dto.key()).isEqualTo("vacancies.cursor");
        assertThat(dto.value()).isEqualTo("2026-11-08T09:30:00Z");
    }

    @Test
    void put_replacesExistingValue() {
        SyncState existing = new SyncState("vacancies.cursor");
        existing.setValue("старое");
        when(repository.findById("vacancies.cursor")).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        service.put("vacancies.cursor", "новое");

        assertThat(existing.getValue()).isEqualTo("новое");
        verify(repository).saveAndFlush(existing);
    }

    @Test
    void put_blankValueOrBadKey_isRejected() {
        assertThatThrownBy(() -> service.put("k", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.put("k", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.put(" ", "v")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.put("k".repeat(101), "v")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }
}
