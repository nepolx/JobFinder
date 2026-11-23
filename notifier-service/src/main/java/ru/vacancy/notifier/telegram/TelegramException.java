package ru.vacancy.notifier.telegram;

public class TelegramException extends RuntimeException {
    public TelegramException(String message) {
        super(message);
    }
}
