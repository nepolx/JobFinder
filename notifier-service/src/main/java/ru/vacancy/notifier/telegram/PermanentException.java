package ru.vacancy.notifier.telegram;

public class PermanentException extends TelegramException {
    public PermanentException(String message) {
        super(message);
    }
}
