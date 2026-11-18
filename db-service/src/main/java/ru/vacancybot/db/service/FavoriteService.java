package ru.vacancybot.db.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vacancybot.common.dto.FavoriteDto;
import ru.vacancybot.db.entity.User;
import ru.vacancybot.db.entity.UserFavorite;
import ru.vacancybot.db.exception.NotFoundException;
import ru.vacancybot.db.mapper.FavoriteMapper;
import ru.vacancybot.db.repository.UserFavoriteRepository;
import ru.vacancybot.db.repository.UserRepository;
import ru.vacancybot.db.repository.VacancyRepository;

import java.util.List;
import java.util.UUID;

@Service
public class FavoriteService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserFavoriteRepository favoriteRepository;
    private final VacancyRepository vacancyRepository;

    public FavoriteService(UserService userService,
                           UserRepository userRepository,
                           UserFavoriteRepository favoriteRepository,
                           VacancyRepository vacancyRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.favoriteRepository = favoriteRepository;
        this.vacancyRepository = vacancyRepository;
    }

    /**
     * Добавляет вакансию в избранное. Повторный вызов не падает и не создаёт дубль:
     * статус и заметка меняются только если переданы (dto может быть null).
     * Из dto используются только status и note. Пользователь создаётся при необходимости.
     * Если вакансии нет в базе, будет 404.
     */
    @Transactional
    public FavoriteDto add(Long telegramId, String vacancyIdText, FavoriteDto dto) {
        UUID vacancyId = FavoriteMapper.parseVacancyId(vacancyIdText);
        if (!vacancyRepository.existsById(vacancyId)) {
            throw new NotFoundException("Вакансия не найдена: " + vacancyIdText);
        }
        User user = userService.getOrCreate(telegramId);

        UserFavorite favorite = favoriteRepository
                .findByUserIdAndVacancyId(user.getId(), vacancyId)
                .orElseGet(() -> new UserFavorite(user.getId(), vacancyId));
        applyChanges(favorite, dto);

        return FavoriteMapper.toDto(favoriteRepository.save(favorite), user.getTelegramId());
    }

    /** Меняет статус и/или заметку. Поля, которые null, остаются как были. */
    @Transactional
    public FavoriteDto update(Long telegramId, String vacancyIdText, FavoriteDto dto) {
        UUID vacancyId = FavoriteMapper.parseVacancyId(vacancyIdText);
        User user = userService.getExisting(telegramId);

        UserFavorite favorite = findFavorite(user, vacancyId, vacancyIdText);
        applyChanges(favorite, dto);

        return FavoriteMapper.toDto(favoriteRepository.save(favorite), user.getTelegramId());
    }

    /** Одна запись избранного, иначе 404. */
    @Transactional(readOnly = true)
    public FavoriteDto get(Long telegramId, String vacancyIdText) {
        UUID vacancyId = FavoriteMapper.parseVacancyId(vacancyIdText);
        User user = userService.getExisting(telegramId);
        return FavoriteMapper.toDto(findFavorite(user, vacancyId, vacancyIdText), user.getTelegramId());
    }

    /** Всё избранное пользователя, новые сверху. Нет пользователя = пустой список. */
    @Transactional(readOnly = true)
    public List<FavoriteDto> list(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .map(user -> favoriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                        .map(f -> FavoriteMapper.toDto(f, user.getTelegramId()))
                        .toList())
                .orElse(List.of());
    }

    @Transactional
    public void remove(Long telegramId, String vacancyIdText) {
        UUID vacancyId = FavoriteMapper.parseVacancyId(vacancyIdText);
        User user = userService.getExisting(telegramId);
        if (!favoriteRepository.existsByUserIdAndVacancyId(user.getId(), vacancyId)) {
            throw new NotFoundException("Вакансии нет в избранном: " + vacancyIdText);
        }
        favoriteRepository.deleteByUserIdAndVacancyId(user.getId(), vacancyId);
    }

    private UserFavorite findFavorite(User user, UUID vacancyId, String vacancyIdText) {
        return favoriteRepository.findByUserIdAndVacancyId(user.getId(), vacancyId)
                .orElseThrow(() -> new NotFoundException("Вакансии нет в избранном: " + vacancyIdText));
    }

    private void applyChanges(UserFavorite favorite, FavoriteDto dto) {
        if (dto == null) {
            return;
        }
        if (dto.status() != null && !dto.status().isBlank()) {
            favorite.setStatus(FavoriteMapper.toDbStatus(dto.status()));
        }
        if (dto.note() != null) {
            // пустая строка = очистить заметку
            favorite.setNote(dto.note().isBlank() ? null : dto.note());
        }
    }
}