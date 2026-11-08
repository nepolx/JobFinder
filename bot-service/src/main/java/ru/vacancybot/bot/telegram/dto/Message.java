package ru.vacancybot.bot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(
        @JsonProperty("message_id") int messageId,
        User from,
        Chat chat,
        String text,
        @JsonProperty("reply_markup") InlineKeyboardMarkup replyMarkup) {
}
