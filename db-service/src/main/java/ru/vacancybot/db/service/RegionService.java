package ru.vacancybot.db.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vacancybot.common.dto.RegionDto;
import ru.vacancybot.db.repository.RegionRepository;

import java.util.List;

/** Справочник регионов (только чтение). */
@Service
public class RegionService {

    private final RegionRepository repository;

    public RegionService(RegionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RegionDto> list() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(r -> new RegionDto(r.getCode(), r.getName()))
                .toList();
    }
}