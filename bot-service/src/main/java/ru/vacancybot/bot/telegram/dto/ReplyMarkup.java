package ru.vacancybot.bot.telegram.dto;

/** Клавиатура, которую можно прикрепить к сообщению: inline-кнопки или постоянное меню под полем ввода. */
public sealed interface ReplyMarkup permits InlineKeyboardMarkup, ReplyKeyboardMarkup {
}
