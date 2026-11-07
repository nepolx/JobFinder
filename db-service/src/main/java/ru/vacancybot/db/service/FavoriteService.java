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

import java.util.List;
import java.util.UUID;

@Service
public class FavoriteService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserFavoriteRepository favoriteRepository;

    public FavoriteService(UserService userService,
                           UserRepository userRepository,
                           UserFavoriteRepository favoriteRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.favoriteRepository = favoriteRepository;
    }

    /**
     * Добавляет вакансию в избранное. Повторный вызов не создаёт дубль,
     * а обновляет статус и заметку (если они переданы).
     * Если вакансии с таким id нет в таблице vacancies, будет ошибка 409.
     */
    @Transactional
    public FavoriteDto add(FavoriteDto dto) {
        UUID vacancyId = FavoriteMapper.parseVacancyId(dto.vacancyId());
        User user = userService.getOrCreate(dto.telegramUserId());

        UserFavorite favorite = favoriteRepository
                .findByUserIdAndVacancyId(user.getId(), vacancyId)
                .orElseGet(() -> new UserFavorite(user.getId(), vacancyId));
        applyChanges(favorite, dto);

        // saveAndFlush, чтобы ошибка внешнего ключа возникла здесь, а не позже при коммите
        return FavoriteMapper.toDto(favoriteRepository.saveAndFlush(favorite), user.getTelegramId());
    }

    /** Меняет статус и/или заметку. Поля, которые null, остаются как были. */
    @Transactional
    public FavoriteDto update(Long telegramId, String vacancyIdText, FavoriteDto dto) {
        UUID vacancyId = FavoriteMapper.parseVacancyId(vacancyIdText);
        User user = userService.getExisting(telegramId);

        UserFavorite favorite = favoriteRepository.findByUserIdAndVacancyId(user.getId(), vacancyId)
                .orElseThrow(() -> new NotFoundException("Вакансии нет в избранном: " + vacancyIdText));
        applyChanges(favorite, dto);

        return FavoriteMapper.toDto(favoriteRepository.save(favorite), user.getTelegramId());
    }

    @Transactional(readOnly = true)
    public List<FavoriteDto> list(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .map(user -> favoriteRepository.findByUserId(user.getId()).stream()
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

    private void applyChanges(UserFavorite favorite, FavoriteDto dto) {
        if (dto.status() != null && !dto.status().isBlank()) {
            favorite.setStatus(FavoriteMapper.toDbStatus(dto.status()));
        }
        if (dto.note() != null) {
            // пустая строка = очистить заметку
            favorite.setNote(dto.note().isBlank() ? null : dto.note());
        }
    }
}