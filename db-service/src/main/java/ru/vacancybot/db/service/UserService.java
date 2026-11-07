package ru.vacancybot.db.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vacancybot.db.entity.User;
import ru.vacancybot.db.exception.NotFoundException;
import ru.vacancybot.db.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Находит пользователя по telegram_id, а если его нет, создаёт. */
    @Transactional
    public User getOrCreate(Long telegramId) {
        if (telegramId == null) {
            throw new IllegalArgumentException("telegramUserId обязателен");
        }
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> userRepository.save(new User(telegramId)));
    }

    /** Находит существующего пользователя, иначе 404. */
    @Transactional(readOnly = true)
    public User getExisting(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + telegramId));
    }
}