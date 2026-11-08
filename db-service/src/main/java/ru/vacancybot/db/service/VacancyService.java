package ru.vacancybot.db.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vacancybot.common.dto.VacancyDto;
import ru.vacancybot.db.mapper.FavoriteMapper;
import ru.vacancybot.db.mapper.VacancyMapper;
import ru.vacancybot.db.repository.VacancyRepository;

import java.util.List;
import java.util.UUID;

@Service
public class VacancyService {

    private static final int MAX_IDS = 100;

    private final VacancyRepository vacancyRepository;

    public VacancyService(VacancyRepository vacancyRepository) {
        this.vacancyRepository = vacancyRepository;
    }

    /** Вакансии по списку id одним запросом. Несуществующие id пропускаются. */
    @Transactional(readOnly = true)
    public List<VacancyDto> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > MAX_IDS) {
            throw new IllegalArgumentException("Слишком много id: максимум " + MAX_IDS);
        }
        List<UUID> uuids = ids.stream().map(FavoriteMapper::parseVacancyId).toList();
        return vacancyRepository.findAllById(uuids).stream()
                .map(VacancyMapper::toDto)
                .toList();
    }
}