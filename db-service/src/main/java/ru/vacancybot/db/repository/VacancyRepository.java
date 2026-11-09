package ru.vacancybot.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.vacancybot.db.entity.Vacancy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VacancyRepository extends JpaRepository<Vacancy, UUID> {

    // Из JpaRepository уже есть: save, findById, findAllById, existsById.

    /** Максимальный modified_at в базе: курсор синхронизации для worker. Пусто, если вакансий нет. */
    @Query("select max(v.modifiedAt) from Vacancy v")
    Optional<Instant> findLastModified();
}