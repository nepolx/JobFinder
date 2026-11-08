package ru.vacancy.notifier.model;

/** Сообщение из очереди не соответствует контракту. */
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
