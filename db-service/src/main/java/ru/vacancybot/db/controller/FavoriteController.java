package ru.vacancybot.db.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vacancybot.common.dto.FavoriteDto;
import ru.vacancybot.db.service.FavoriteService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class FavoriteController {

    private final FavoriteService service;

    public FavoriteController(FavoriteService service) {
        this.service = service;
    }

    // POST /api/favorites  (тело: FavoriteDto, нужны telegramUserId и vacancyId; status и note по желанию)
    @PostMapping("/favorites")
    public FavoriteDto add(@RequestBody FavoriteDto dto) {
        return service.add(dto);
    }

    // GET /api/users/{telegramId}/favorites
    @GetMapping("/users/{telegramId}/favorites")
    public List<FavoriteDto> list(@PathVariable("telegramId") Long telegramId) {
        return service.list(telegramId);
    }

    // PATCH /api/users/{telegramId}/favorites/{vacancyId}  (тело: FavoriteDto, используются status и note)
    @PatchMapping("/users/{telegramId}/favorites/{vacancyId}")
    public FavoriteDto update(@PathVariable("telegramId") Long telegramId,
                              @PathVariable("vacancyId") String vacancyId,
                              @RequestBody FavoriteDto dto) {
        return service.update(telegramId, vacancyId, dto);
    }

    // DELETE /api/users/{telegramId}/favorites/{vacancyId}
    @DeleteMapping("/users/{telegramId}/favorites/{vacancyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable("telegramId") Long telegramId,
                       @PathVariable("vacancyId") String vacancyId) {
        service.remove(telegramId, vacancyId);
    }
}