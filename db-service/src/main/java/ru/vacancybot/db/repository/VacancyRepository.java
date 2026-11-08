package ru.vacancybot.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vacancybot.db.entity.Vacancy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VacancyRepository extends JpaRepository<Vacancy, UUID> {

    // Из JpaRepository уже есть: save, findById, findAllById, existsById.

    /** Максимальный modified_at в базе: курсор синхронизации для worker. Пусто, если вакансий нет. */
    @Query("select max(v.modifiedAt) from Vacancy v")
    Optional<Instant> findLastModified();

    /**
     * Подписчики, которым подходит вакансия (для уведомления о новой вакансии).
     *
     * Правила совпадения (пустой критерий подписки = «не важно»):
     *  - регион: коды совпадают;
     *  - зарплата: верхняя граница вакансии (а если её нет, то нижняя) не меньше желаемого минимума;
     *    вакансия без указанной зарплаты под подписку с минимумом НЕ подходит;
     *  - опыт: опыт вакансии (если не указан, то 0) попадает в диапазон подписки;
     *  - график и тип занятости: коды совпадают (у вакансии код не указан = не подходит);
     *  - слова: полнотекстовый поиск по названию, обязанностям и требованиям
     *    (в подписке все слова из названия и ключевых слов должны встретиться).
     *
     * Один пользователь с несколькими подходящими подписками возвращается один раз
     * (берётся подписка с наименьшим id).
     */
    @Query(nativeQuery = true, value = """
            select distinct on (u.telegram_id)
                   u.telegram_id as "telegramId",
                   s.id          as "subscriptionId"
            from vacancies v
            join subscriptions s on s.is_active
            join users u on u.id = s.user_id
            where v.id = :vacancyId
              and (s.region_code     is null or s.region_code = v.region_code)
              and (s.salary_min      is null or coalesce(v.salary_max, v.salary_min) >= s.salary_min)
              and (s.experience_from is null or coalesce(v.experience_years, 0) >= s.experience_from)
              and (s.experience_to   is null or coalesce(v.experience_years, 0) <= s.experience_to)
              and (s.schedule_code   is null or s.schedule_code = v.schedule_code)
              and (s.employment_code is null or s.employment_code = v.employment_code)
              and (s.query_tsq       is null or v.search_vector @@ s.query_tsq)
            order by u.telegram_id, s.id
            """)
    List<SubscriberMatch> findSubscribersFor(@Param("vacancyId") UUID vacancyId);

    /** Telegram id пользователей, у которых вакансия в избранном (для уведомления об изменении). */
    @Query(nativeQuery = true, value = """
            select u.telegram_id
            from user_favorites f
            join users u on u.id = f.user_id
            where f.vacancy_id = :vacancyId
            order by u.telegram_id
            """)
    List<Long> findFavoriteFollowers(@Param("vacancyId") UUID vacancyId);
}