package ru.vacancy.notifier.telegram;

/** Повтор не поможет (например, некорректный запрос). */
public class PermanentException extends TelegramException {
    public PermanentException(String message) {
        super(message);
    }
}
