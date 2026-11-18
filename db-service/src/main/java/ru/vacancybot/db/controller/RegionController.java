package ru.vacancybot.db.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vacancybot.common.dto.RegionDto;
import ru.vacancybot.db.service.RegionService;

import java.util.List;

/** Справочник регионов для bot-service. */
@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService service;

    public RegionController(RegionService service) {
        this.service = service;
    }

    // GET /api/regions  (все регионы по алфавиту; code = то, что бот кладёт в SubscriptionDto.region)
    @GetMapping
    public List<RegionDto> list() {
        return service.list();
    }
}