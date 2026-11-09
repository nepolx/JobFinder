package ru.vacancybot.bot.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CallbackQuery(String id, User from, Message message, String data) {
}
