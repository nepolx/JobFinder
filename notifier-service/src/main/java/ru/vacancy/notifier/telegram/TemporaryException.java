package ru.vacancy.notifier.telegram;

/** Сбой, который может пройти сам: сеть, 5xx, flood control. Нужен повтор. */
public class TemporaryException extends TelegramException {
    public TemporaryException(String message) {
        super(message);
    }
}
