package ru.vacancy.notifier.telegram;

public interface TelegramSender {

    /** @throws TelegramException при любой неудаче; тип исключения говорит, повторять ли. */
    void sendMessage(long chatId, String text);
}
