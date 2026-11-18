package ru.vacancybot.bot.handler;

import ru.vacancybot.bot.telegram.dto.InlineKeyboardMarkup;

/** Нажатие inline-кнопки: всё, что нужно обработчикам, без зависимости от формы Update. */
public record CallbackCtx(
        String queryId,
        long chatId,
        long userId,
        int messageId,
        String messageText,
        InlineKeyboardMarkup markup,
        String data) {
}
