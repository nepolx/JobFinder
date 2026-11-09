package ru.vacancybot.bot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InlineKeyboardMarkup(
        @JsonProperty("inline_keyboard") List<List<InlineKeyboardButton>> inlineKeyboard) {

    public static InlineKeyboardMarkup empty() {
        return new InlineKeyboardMarkup(List.of());
    }

    @JsonIgnore
    public boolean isEmpty() {
        return inlineKeyboard == null || inlineKeyboard.isEmpty();
    }
}
