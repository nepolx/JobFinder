package ru.vacancybot.db.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vacancybot.db.entity.VacancyChange;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VacancyChangeRepository extends JpaRepository<VacancyChange, Long> {

    /** История одной вакансии, новые сверху. */
    List<VacancyChange> findByVacancyIdOrderByChangedAtDesc(UUID vacancyId);

    /**
     * События, которые ещё не опубликованы в RabbitMQ, старые первыми.
     * События, у которых неудачных публикаций уже maxAttempts и больше, пропускаются,
     * чтобы одно «битое» событие не блокировало остальные.
     */
    List<VacancyChange> findByPublishedAtIsNullAndPublishAttemptsLessThanOrderByIdAsc(
            int maxAttempts, Pageable pageable);

    /**
     * Ставит отметку «опубликовано». Ключ полный (id + changed_at): таблица партиционирована
     * по changed_at, так база заглядывает только в нужную партицию.
     * Возвращает 1, если отметка поставлена сейчас, и 0, если события нет или оно уже помечено.
     */
    @Modifying
    @Query("update VacancyChange c set c.publishedAt = :now "
            + "where c.id = :id and c.changedAt = :changedAt and c.publishedAt is null")
    int markPublished(@Param("id") Long id,
                      @Param("changedAt") Instant changedAt,
                      @Param("now") Instant now);

    /** Учитывает неудачную публикацию: счётчик + 1 и текст последней ошибки. */
    @Modifying
    @Query("update VacancyChange c set c.publishAttempts = c.publishAttempts + 1, "
            + "c.lastPublishError = :error "
            + "where c.id = :id and c.changedAt = :changedAt")
    int registerPublishFailure(@Param("id") Long id,
                               @Param("changedAt") Instant changedAt,
                               @Param("error") String error);
}