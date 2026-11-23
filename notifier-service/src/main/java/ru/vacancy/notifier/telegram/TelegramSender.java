package ru.vacancy.notifier.telegram;

public interface TelegramSender {

    /** @throws TelegramException */
    void sendMessage(long chatId, String text);
}
