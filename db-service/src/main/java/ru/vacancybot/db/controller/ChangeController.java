package ru.vacancybot.db.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.vacancybot.common.dto.ChangeKeyDto;
import ru.vacancybot.common.dto.PublishFailedDto;
import ru.vacancybot.common.dto.VacancyChangeDto;
import ru.vacancybot.db.service.ChangePublicationService;

import java.util.List;
import java.util.Map;

/** Учёт публикации событий в RabbitMQ (outbox) для worker. */
@RestController
@RequestMapping("/api/changes")
public class ChangeController {

    private final ChangePublicationService service;

    public ChangeController(ChangePublicationService service) {
        this.service = service;
    }

    // GET /api/changes/unpublished?limit=200  (события, которые ещё не опубликованы, старые первыми)
    @GetMapping("/unpublished")
    public List<VacancyChangeDto> unpublished(@RequestParam(required = false) Integer limit) {
        return service.unpublished(limit);
    }

    // POST /api/changes/published  (тело: [{"changeId":..., "changedAt":"..."}]; ответ: {"marked": n})
    @PostMapping("/published")
    public Map<String, Integer> published(@RequestBody List<ChangeKeyDto> keys) {
        return Map.of("marked", service.markPublished(keys));
    }

    // POST /api/changes/publish-failed  (тело: {"changeId":..., "changedAt":"...", "error":"..."})
    @PostMapping("/publish-failed")
    public Map<String, Integer> publishFailed(@RequestBody PublishFailedDto body) {
        return Map.of("updated", service.publishFailed(body));
    }
}