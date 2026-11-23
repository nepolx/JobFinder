package ru.vacancy.notifier.telegram;

public class BotBlockedException extends PermanentException {
    public BotBlockedException(String message) {
        super(message);
    }
}
