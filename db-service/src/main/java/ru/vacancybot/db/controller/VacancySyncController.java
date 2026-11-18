package ru.vacancybot.db.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vacancybot.common.dto.VacancyChangeDto;
import ru.vacancybot.common.dto.VacancyUpsertDto;
import ru.vacancybot.db.repository.VacancyRepository;
import ru.vacancybot.db.service.VacancySyncService;

import java.time.Instant;
import java.util.List;

/** Эндпоинты для worker */
@RestController
@RequestMapping("/api/vacancies")
public class VacancySyncController {

    private final VacancySyncService syncService;
    private final VacancyRepository vacancyRepository;

    public VacancySyncController(VacancySyncService syncService, VacancyRepository vacancyRepository) {
        this.syncService = syncService;
        this.vacancyRepository = vacancyRepository;
    }

    // POST /api/vacancies/sync  (тело: список VacancyUpsertDto, до 100; ответ: события created/updated)
    @PostMapping("/sync")
    public List<VacancyChangeDto> sync(@RequestBody List<VacancyUpsertDto> items) {
        return syncService.sync(items);
    }

    // GET /api/vacancies/last-modified  (максимальный modified_at в базе; пустой ответ, если вакансий нет)
    @GetMapping("/last-modified")
    public Instant lastModified() {
        return vacancyRepository.findLastModified().orElse(null);
    }


}