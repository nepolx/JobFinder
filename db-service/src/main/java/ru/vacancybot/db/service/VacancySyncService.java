package ru.vacancybot.db.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vacancybot.common.dto.VacancyChangeDto;
import ru.vacancybot.common.dto.VacancyUpsertDto;
import ru.vacancybot.db.entity.Vacancy;
import ru.vacancybot.db.mapper.VacancyUpsertMapper;
import ru.vacancybot.db.repository.VacancyRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Приём вакансий от worker: сохраняет новые и изменённые, возвращает события.
 * Само сравнение делает VacancyComparator.
 */
@Service
public class VacancySyncService {

    private static final int MAX_BATCH = 100;

    private final VacancyRepository vacancyRepository;
    private final VacancyComparator comparator;
    private final VacancyChangeRecorder recorder;

    public VacancySyncService(VacancyRepository vacancyRepository,
                              VacancyComparator comparator,
                              VacancyChangeRecorder recorder) {
        this.vacancyRepository = vacancyRepository;
        this.comparator = comparator;
        this.recorder = recorder;
    }

    /** Возвращает события только по тем вакансиям, где что-то произошло. */
    @Transactional
    public List<VacancyChangeDto> sync(List<VacancyUpsertDto> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (items.size() > MAX_BATCH) {
            throw new IllegalArgumentException("Слишком много вакансий: максимум " + MAX_BATCH);
        }

        List<VacancyChangeDto> events = new ArrayList<>();
        for (VacancyUpsertDto dto : items) {
            UUID id = VacancyUpsertMapper.parseId(dto.id());
            Optional<Vacancy> existing = vacancyRepository.findById(id);

            if (existing.isEmpty()) {
                events.add(recorder.recordCreated(VacancyUpsertMapper.toEntity(dto)));
                continue;
            }

            Vacancy old = existing.get();
            Vacancy incoming = VacancyUpsertMapper.toEntity(dto); // только для сравнения, не сохраняется
            if (!incoming.getModifiedAt().isAfter(old.getModifiedAt())) {
                continue; // версия не новее той, что в базе
            }

            // сравнение до применения изменений: old ещё хранит прежние значения
            Map<String, List<Object>> changes = comparator.diff(old, incoming);

            VacancyUpsertMapper.applyTo(dto, old);
            if (changes == null || changes.isEmpty()) {
                vacancyRepository.save(old); // сдвинулся только modified_at, события нет
            } else {
                events.add(recorder.recordUpdated(old, changes));
            }
        }
        return events;
    }
}