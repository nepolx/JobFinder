package ru.vacancybot.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vacancybot.db.entity.Subscription;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

    // все подписки пользователя
    List<Subscription> findByUserId(Integer userId);

    Optional<Subscription> findByIdAndUserId(Integer id, Integer userId);

    long countByUserId(Integer userId);
}