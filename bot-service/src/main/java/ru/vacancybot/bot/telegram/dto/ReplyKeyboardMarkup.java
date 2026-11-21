package ru.vacancybot.bot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Постоянное меню под полем ввода («горячие клавиши»). Нажатие на кнопку отправляет её текст как сообщение. */
public record ReplyKeyboardMarkup(
        List<List<KeyboardButton>> keyboard,
        @JsonProperty("resize_keyboard") boolean resizeKeyboard,
        @JsonProperty("is_persistent") boolean persistent) implements ReplyMarkup {
}
