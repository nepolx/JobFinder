package ru.vacancybot.db.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vacancybot.common.dto.SyncStateDto;
import ru.vacancybot.common.dto.SyncStateValueDto;
import ru.vacancybot.db.service.SyncStateService;

/** Курсор и другие значения синхронизации для worker. */
@RestController
@RequestMapping("/api/sync-state")
public class SyncStateController {

    private final SyncStateService service;

    public SyncStateController(SyncStateService service) {
        this.service = service;
    }

    // GET /api/sync-state/{key}  (404, если ключа ещё нет)
    @GetMapping("/{key}")
    public SyncStateDto get(@PathVariable String key) {
        return service.get(key);
    }

    // PUT /api/sync-state/{key}  (тело: {"value": "..."}; создаёт или заменяет)
    @PutMapping("/{key}")
    public SyncStateDto put(@PathVariable String key, @RequestBody SyncStateValueDto body) {
        return service.put(key, body == null ? null : body.value());
    }
}