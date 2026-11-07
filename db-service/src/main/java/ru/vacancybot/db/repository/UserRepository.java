package ru.vacancybot.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vacancybot.db.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    // select * from users where telegram_id = ?
    Optional<User> findByTelegramId(Long telegramId);
}