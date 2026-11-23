package ru.vacancy.notifier.telegram;

public interface TelegramSender {

    /** @throws TelegramException при любой неудаче; тип исключения говорит, повторять ли. */
    void sendMessage(long chatId, String text);

    /**
     * То же, но с inline-кнопками под сообщением (keyboard может быть null).
     * Реализация по умолчанию кнопки отбрасывает: настоящий отправитель ({@link HttpTelegramSender}) её переопределяет.
     */
    default void sendMessage(long chatId, String text, InlineKeyboard keyboard) {
        sendMessage(chatId, text);
    }
}
