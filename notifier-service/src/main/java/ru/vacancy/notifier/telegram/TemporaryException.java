package ru.vacancy.notifier.telegram;

public class TemporaryException extends TelegramException {
    public TemporaryException(String message) {
        super(message);
    }
}
