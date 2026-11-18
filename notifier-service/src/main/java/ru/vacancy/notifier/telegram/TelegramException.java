package ru.vacancy.notifier.telegram;

/** Базовая ошибка отправки; подклассы показывают, что с ней делать. */
public class TelegramException extends RuntimeException {
    public TelegramException(String message) {
        super(message);
    }
}
