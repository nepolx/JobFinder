package ru.vacancybot.bot.telegram;

import ru.vacancybot.bot.telegram.dto.BotCommand;
import ru.vacancybot.bot.telegram.dto.InlineKeyboardMarkup;
import ru.vacancybot.bot.telegram.dto.ReplyMarkup;

import java.util.List;

/**
 * Узкий интерфейс к Telegram Bot API. Обработчики зависят только от него,
 * поэтому в тестах подставляется фейк, а реальная реализация — {@link RestTelegramClient}.
 * Простой текст без parse_mode: пользовательский ввод не нужно экранировать.
 */
public interface TelegramClient {

    /** @return message_id отправленного сообщения */
    int sendMessage(long chatId, String text, ReplyMarkup keyboard);

    void editMessageText(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard);

    /** keyboard == null (или пустая) — убрать кнопки. */
    void editMessageReplyMarkup(long chatId, int messageId, InlineKeyboardMarkup keyboard);

    /** Удаляет сообщение (бот может удалять свои сообщения и сообщения пользователя в личке, если им меньше 48 часов). */
    void deleteMessage(long chatId, int messageId);

    /** Нужно вызывать на каждый callback, иначе у кнопки «крутится» индикатор. text может быть null. */
    void answerCallbackQuery(String callbackQueryId, String text);

    void setWebhook(String url, String secretToken);

    void setMyCommands(List<BotCommand> commands);
}
