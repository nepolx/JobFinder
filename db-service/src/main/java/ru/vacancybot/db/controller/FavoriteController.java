package ru.vacancybot.db.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vacancybot.common.dto.FavoriteDto;
import ru.vacancybot.db.service.FavoriteService;

import java.util.List;

@RestController
@RequestMapping("/api/users/{telegramId}/favorites")
public class FavoriteController {

    private final FavoriteService service;

    public FavoriteController(FavoriteService service) {
        this.service = service;
    }

    // GET /api/users/{telegramId}/favorites  (команда /favorites)
    @GetMapping
    public List<FavoriteDto> list(@PathVariable("telegramId") Long telegramId) {
        return service.list(telegramId);
    }

    // GET /api/users/{telegramId}/favorites/{vacancyId}  (404, если нет в избранном)
    @GetMapping("/{vacancyId}")
    public FavoriteDto get(@PathVariable("telegramId") Long telegramId,
                           @PathVariable("vacancyId") String vacancyId) {
        return service.get(telegramId, vacancyId);
    }

    // PUT /api/users/{telegramId}/favorites/{vacancyId}  (fav_add_{id}; тело необязательно)
    // Из тела FavoriteDto используются только status и note.
    @PutMapping("/{vacancyId}")
    public FavoriteDto add(@PathVariable("telegramId") Long telegramId,
                           @PathVariable("vacancyId") String vacancyId,
                           @RequestBody(required = false) FavoriteDto dto) {
        return service.add(telegramId, vacancyId, dto);
    }

    // PATCH /api/users/{telegramId}/favorites/{vacancyId}  (смена статуса или заметки)
    @PatchMapping("/{vacancyId}")
    public FavoriteDto update(@PathVariable("telegramId") Long telegramId,
                              @PathVariable("vacancyId") String vacancyId,
                              @RequestBody FavoriteDto dto) {
        return service.update(telegramId, vacancyId, dto);
    }

    // DELETE /api/users/{telegramId}/favorites/{vacancyId}  (fav_del_{id})
    @DeleteMapping("/{vacancyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable("telegramId") Long telegramId,
                       @PathVariable("vacancyId") String vacancyId) {
        service.remove(telegramId, vacancyId);
    }
}