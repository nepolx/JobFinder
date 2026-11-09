package ru.vacancybot.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vacancybot.db.entity.VacancyChange;

import java.util.List;
import java.util.UUID;

public interface VacancyChangeRepository extends JpaRepository<VacancyChange, Long> {

    /** История одной вакансии, новые сверху. */
    List<VacancyChange> findByVacancyIdOrderByChangedAtDesc(UUID vacancyId);
}