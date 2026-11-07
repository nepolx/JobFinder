package ru.vacancybot.db.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vacancybot.common.dto.SubscriptionDto;
import ru.vacancybot.db.service.SubscriptionService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    // POST /api/subscriptions  (sub_confirm; тело: SubscriptionDto, telegramUserId обязателен)
    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionDto create(@RequestBody SubscriptionDto dto) {
        return service.create(dto);
    }

    // GET /api/users/{telegramId}/subscriptions?onlyActive=true  (команда /subscriptions)
    @GetMapping("/users/{telegramId}/subscriptions")
    public List<SubscriptionDto> list(@PathVariable("telegramId") Long telegramId,
                                      @RequestParam(name = "onlyActive", defaultValue = "false") boolean onlyActive) {
        return service.list(telegramId, onlyActive);
    }

    // PUT /api/users/{telegramId}/subscriptions/{id}  (кнопка «Изменить»; тело: SubscriptionDto)
    @PutMapping("/users/{telegramId}/subscriptions/{id}")
    public SubscriptionDto update(@PathVariable("telegramId") Long telegramId,
                                  @PathVariable("id") Integer id,
                                  @RequestBody SubscriptionDto dto) {
        return service.update(telegramId, id, dto);
    }

    // PATCH /api/users/{telegramId}/subscriptions/{id}/active?value=false  (sub_del_{id} = деактивация)
    @PatchMapping("/users/{telegramId}/subscriptions/{id}/active")
    public SubscriptionDto setActive(@PathVariable("telegramId") Long telegramId,
                                     @PathVariable("id") Integer id,
                                     @RequestParam("value") boolean value) {
        return service.setActive(telegramId, id, value);
    }

    // DELETE /api/users/{telegramId}/subscriptions/{id}  (физическое удаление, обычно не нужно)
    @DeleteMapping("/users/{telegramId}/subscriptions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("telegramId") Long telegramId,
                       @PathVariable("id") Integer id) {
        service.delete(telegramId, id);
    }
}