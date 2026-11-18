package ru.vacancybot.db.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vacancybot.common.dto.VacancyDto;
import ru.vacancybot.db.service.VacancyService;

import java.util.List;

@RestController
@RequestMapping("/api/vacancies")
public class VacancyController {

    private final VacancyService service;

    public VacancyController(VacancyService service) {
        this.service = service;
    }

    // GET /api/vacancies?ids=uuid1,uuid2,uuid3  (карточки для /favorites, максимум 100 id)
    @GetMapping
    public List<VacancyDto> byIds(@RequestParam("ids") List<String> ids) {
        return service.findByIds(ids);
    }
}