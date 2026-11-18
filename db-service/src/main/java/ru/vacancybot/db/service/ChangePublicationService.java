package ru.vacancybot.db.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vacancybot.common.dto.ChangeKeyDto;
import ru.vacancybot.common.dto.PublishFailedDto;
import ru.vacancybot.common.dto.VacancyChangeDto;
import ru.vacancybot.db.mapper.VacancyChangeMapper;
import ru.vacancybot.db.repository.VacancyChangeRepository;

import java.time.Instant;
import java.util.List;

/**
 * Учёт публикации событий в RabbitMQ (outbox): какие события ещё не опубликованы,
 * какие уже опубликованы и какие не удалось опубликовать.
 */
@Service
public class ChangePublicationService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;
    private static final int MAX_KEYS = 1000;
    private static final int MAX_ERROR_LENGTH = 500;

    private final VacancyChangeRepository repository;
    private final int maxPublishAttempts;

    public ChangePublicationService(VacancyChangeRepository repository,
                                    @Value("${app.outbox.max-publish-attempts:5}") int maxPublishAttempts) {
        this.repository = repository;
        this.maxPublishAttempts = maxPublishAttempts;
    }

    /** Неопубликованные события, старые первыми. limit по умолчанию 200, максимум 1000. */
    @Transactional(readOnly = true)
    public List<VacancyChangeDto> unpublished(Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1) {
            throw new IllegalArgumentException("limit должен быть больше 0");
        }
        size = Math.min(size, MAX_LIMIT);
        return repository
                .findByPublishedAtIsNullAndPublishAttemptsLessThanOrderByIdAsc(maxPublishAttempts, PageRequest.of(0, size))
                .stream()
                .map(VacancyChangeMapper::toDto)
                .toList();
    }

    /** Помечает события опубликованными. Повтор и неизвестные ключи не дают ошибку. Возвращает число помеченных сейчас. */
    @Transactional
    public int markPublished(List<ChangeKeyDto> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        if (keys.size() > MAX_KEYS) {
            throw new IllegalArgumentException("Слишком много ключей: максимум " + MAX_KEYS);
        }
        Instant now = Instant.now();
        int marked = 0;
        for (ChangeKeyDto key : keys) {
            if (key == null || key.changeId() == null || key.changedAt() == null) {
                throw new IllegalArgumentException("У каждого ключа должны быть changeId и changedAt");
            }
            marked += repository.markPublished(key.changeId(), key.changedAt(), now);
        }
        return marked;
    }

    /** Учитывает неудачную публикацию. Возвращает число обновлённых записей (0, если такого события нет). */
    @Transactional
    public int publishFailed(PublishFailedDto dto) {
        if (dto == null || dto.changeId() == null || dto.changedAt() == null) {
            throw new IllegalArgumentException("Нужны changeId и changedAt");
        }
        String error = dto.error();
        if (error != null && error.length() > MAX_ERROR_LENGTH) {
            error = error.substring(0, MAX_ERROR_LENGTH);
        }
        return repository.registerPublishFailure(dto.changeId(), dto.changedAt(), error);
    }
}