package ru.vacancybot.db.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.vacancybot.db.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // POST /api/users/{telegramId}  (команда /start; безопасно вызывать повторно)
    @PostMapping("/{telegramId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void register(@PathVariable("telegramId") Long telegramId) {
        userService.getOrCreate(telegramId);
    }
}