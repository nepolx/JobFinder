package ru.vacancy.notifier.telegram;

/** Пользователь заблокировал бота или чат больше не существует. */
public class BotBlockedException extends PermanentException {
    public BotBlockedException(String message) {
        super(message);
    }
}
