package ru.vacancybot.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vacancybot.db.entity.UserFavorite;
import ru.vacancybot.db.entity.UserFavoriteId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UserFavoriteId> {

    List<UserFavorite> findByUserId(Integer userId);

    Optional<UserFavorite> findByUserIdAndVacancyId(Integer userId, UUID vacancyId);

    boolean existsByUserIdAndVacancyId(Integer userId, UUID vacancyId);

    // удаление работает только внутри транзакции (@Transactional в сервисе)
    void deleteByUserIdAndVacancyId(Integer userId, UUID vacancyId);
}