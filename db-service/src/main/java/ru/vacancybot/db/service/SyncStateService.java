package ru.vacancybot.db.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vacancybot.common.dto.SyncStateDto;
import ru.vacancybot.db.entity.SyncState;
import ru.vacancybot.db.exception.NotFoundException;
import ru.vacancybot.db.repository.SyncStateRepository;

/** Хранилище значений синхронизации (например, курсор vacancies.cursor). */
@Service
public class SyncStateService {

    private static final int MAX_KEY_LENGTH = 100;

    private final SyncStateRepository repository;

    public SyncStateService(SyncStateRepository repository) {
        this.repository = repository;
    }

    /** Значение по ключу, иначе 404. */
    @Transactional(readOnly = true)
    public SyncStateDto get(String key) {
        return repository.findById(key)
                .map(SyncStateService::toDto)
                .orElseThrow(() -> new NotFoundException("Ключ не найден: " + key));
    }

    /** Создаёт значение или заменяет существующее. Повтор с тем же значением безопасен. */
    @Transactional
    public SyncStateDto put(String key, String value) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Ключ не должен быть пустым или длиннее " + MAX_KEY_LENGTH);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value обязателен");
        }
        SyncState state = repository.findById(key).orElseGet(() -> new SyncState(key));
        state.setValue(value);
        return toDto(repository.saveAndFlush(state));
    }

    private static SyncStateDto toDto(SyncState s) {
        return new SyncStateDto(s.getKey(), s.getValue(), s.getUpdatedAt());
    }
}