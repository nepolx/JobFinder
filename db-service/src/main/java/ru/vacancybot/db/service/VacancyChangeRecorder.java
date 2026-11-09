package ru.vacancybot.db.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vacancybot.common.dto.VacancyChangeDto;
import ru.vacancybot.db.entity.Vacancy;
import ru.vacancybot.db.entity.VacancyChange;
import ru.vacancybot.db.repository.VacancyChangeRepository;
import ru.vacancybot.db.repository.VacancyRepository;

import java.util.List;
import java.util.Map;

/** Сохраняет вакансию вместе с записью в историю (vacancy_changes) в одной транзакции. */
@Service
public class VacancyChangeRecorder {

    private final VacancyRepository vacancyRepository;
    private final VacancyChangeRepository changeRepository;

    public VacancyChangeRecorder(VacancyRepository vacancyRepository,
                                 VacancyChangeRepository changeRepository) {
        this.vacancyRepository = vacancyRepository;
        this.changeRepository = changeRepository;
    }

    /** Новая вакансия: сохраняет её и пишет событие "created" (changes = null). */
    @Transactional
    public VacancyChangeDto recordCreated(Vacancy vacancy) {
        // сначала вакансия (внешний ключ), потом событие
        vacancyRepository.saveAndFlush(vacancy);
        VacancyChange change = changeRepository.saveAndFlush(
                new VacancyChange(vacancy.getId(), VacancyChange.CREATED, null));
        return toDto(change);
    }

    /** Изменённая вакансия: сохраняет новое состояние и пишет событие "updated" с перечнем изменений. */
    @Transactional
    public VacancyChangeDto recordUpdated(Vacancy vacancy, Map<String, List<Object>> changes) {
        vacancyRepository.saveAndFlush(vacancy);
        VacancyChange change = changeRepository.saveAndFlush(
                new VacancyChange(vacancy.getId(), VacancyChange.UPDATED, changes));
        return toDto(change);
    }

    private static VacancyChangeDto toDto(VacancyChange c) {
        return new VacancyChangeDto(
                c.getId(),
                c.getChangedAt(),
                c.getVacancyId().toString(),
                c.getChangeType(),
                c.getChanges());
    }
}