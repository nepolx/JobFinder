package ru.vacancybot.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vacancybot.db.entity.Subscription;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Integer> {

    // все подписки пользователя
    List<Subscription> findByUserId(Integer userId);

    // подписка по id, но только если она принадлежит этому пользователю
    // (так один пользователь не сможет изменить или удалить чужую подписку)
    Optional<Subscription> findByIdAndUserId(Integer id, Integer userId);

    long countByUserId(Integer userId);
}