package ru.vacancybot.db.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.vacancybot.common.dto.VacancyChangeDto;
import ru.vacancybot.common.dto.VacancyUpsertDto;
import ru.vacancybot.db.entity.Vacancy;
import ru.vacancybot.db.mapper.VacancyUpsertMapper;
import ru.vacancybot.db.repository.VacancyRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacancySyncServiceTest {

    private static final String ID = "11111111-1111-1111-1111-111111111111";
    private static final Instant T1 = Instant.parse("2026-11-08T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-08T12:00:00Z");

    @Mock VacancyRepository vacancyRepository;
    @Mock VacancyComparator comparator;
    @Mock VacancyChangeRecorder recorder;

    VacancySyncService service;

    @BeforeEach
    void setUp() {
        service = new VacancySyncService(vacancyRepository, comparator, recorder);
    }

    private static VacancyUpsertDto dto(Instant modifiedAt) {
        return new VacancyUpsertDto(ID, "ООО Ромашка", "7700000000000", "Город Москва", "Java",
                100_000, 150_000, "Полный", "full", "remote", 3, "дело", "треб",
                "https://trudvsem.ru/vacancy/card/1", modifiedAt);
    }

    private static Vacancy stored(Instant modifiedAt) {
        return VacancyUpsertMapper.toEntity(dto(modifiedAt));
    }

    private static VacancyChangeDto event(String type) {
        return new VacancyChangeDto(1L, T2, ID, type, null);
    }

    @Test
    void emptyOrNullBatch_returnsEmptyList_andTouchesNothing() {
        assertThat(service.sync(null)).isEmpty();
        assertThat(service.sync(List.of())).isEmpty();
        verifyNoInteractions(vacancyRepository, comparator, recorder);
    }

    @Test
    void batchLargerThan100_isRejected() {
        List<VacancyUpsertDto> items = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            items.add(dto(T1));
        }
        assertThatThrownBy(() -> service.sync(items))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");
    }

    @Test
    void newVacancy_recordsCreatedEvent() {
        when(vacancyRepository.findById(UUID.fromString(ID))).thenReturn(Optional.empty());
        when(recorder.recordCreated(any())).thenReturn(event("created"));

        List<VacancyChangeDto> result = service.sync(List.of(dto(T1)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).changeType()).isEqualTo("created");
        verifyNoInteractions(comparator);
    }

    @Test
    void sameOrOlderModifiedAt_isSkipped() {
        when(vacancyRepository.findById(UUID.fromString(ID))).thenReturn(Optional.of(stored(T2)));

        assertThat(service.sync(List.of(dto(T2)))).isEmpty();   // такая же версия
        assertThat(service.sync(List.of(dto(T1)))).isEmpty();   // более старая

        verifyNoInteractions(comparator, recorder);
        verify(vacancyRepository, never()).save(any());
    }

    @Test
    void newerWithDifferences_recordsUpdatedEvent_withChanges() {
        Vacancy old = stored(T1);
        when(vacancyRepository.findById(UUID.fromString(ID))).thenReturn(Optional.of(old));
        Map<String, List<Object>> changes = Map.of("salary_min", List.of(100_000, 130_000));
        when(comparator.diff(any(), any())).thenReturn(changes);
        when(recorder.recordUpdated(any(), any())).thenReturn(event("updated"));

        List<VacancyChangeDto> result = service.sync(List.of(dto(T2)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).changeType()).isEqualTo("updated");
        verify(recorder).recordUpdated(old, changes);
        verify(vacancyRepository, never()).save(any());
    }

    @Test
    void comparatorReceivesIncomingVersion() {
        Vacancy old = stored(T1);
        when(vacancyRepository.findById(UUID.fromString(ID))).thenReturn(Optional.of(old));
        when(comparator.diff(any(), any())).thenReturn(Map.of());

        VacancyUpsertDto changed = new VacancyUpsertDto(ID, "ООО Ромашка", "7700000000000", "Город Москва",
                "Java", 130_000, 150_000, "Полный", "full", "remote", 3, "дело", "треб",
                "https://trudvsem.ru/vacancy/card/1", T2);

        service.sync(List.of(changed));

        ArgumentCaptor<Vacancy> newCaptor = ArgumentCaptor.forClass(Vacancy.class);
        verify(comparator).diff(any(), newCaptor.capture());
        assertThat(newCaptor.getValue().getSalaryMin()).isEqualTo(130_000);
    }

    @Test
    void newerButNoDifferences_onlySavesNewModifiedAt_noEvent() {
        Vacancy old = stored(T1);
        when(vacancyRepository.findById(UUID.fromString(ID))).thenReturn(Optional.of(old));
        when(comparator.diff(any(), any())).thenReturn(Map.of());

        List<VacancyChangeDto> result = service.sync(List.of(dto(T2)));

        assertThat(result).isEmpty();
        assertThat(old.getModifiedAt()).isEqualTo(T2);
        verify(vacancyRepository).save(old);
        verifyNoInteractions(recorder);
    }

    @Test
    void mixedBatch_returnsEventsOnlyForChangedVacancies() {
        String id2 = "22222222-2222-2222-2222-222222222222";
        when(vacancyRepository.findById(UUID.fromString(ID))).thenReturn(Optional.empty());
        when(vacancyRepository.findById(UUID.fromString(id2))).thenReturn(Optional.of(stored(T2)));
        when(recorder.recordCreated(any())).thenReturn(event("created"));

        VacancyUpsertDto second = new VacancyUpsertDto(id2, "ООО", "7700000000000", "Москва", "Java",
                1, 2, null, null, null, null, null, null, "https://x", T1); // старее, будет пропущена

        List<VacancyChangeDto> result = service.sync(List.of(dto(T1), second));

        assertThat(result).hasSize(1);
    }
}
