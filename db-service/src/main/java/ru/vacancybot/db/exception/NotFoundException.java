package ru.vacancybot.db.exception;

/** Объект не найден (пользователь, подписка, избранное). Превращается в HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}