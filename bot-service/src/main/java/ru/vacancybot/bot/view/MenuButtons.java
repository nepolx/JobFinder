package ru.vacancybot.bot.view;

import ru.vacancybot.bot.telegram.dto.KeyboardButton;
import ru.vacancybot.bot.telegram.dto.ReplyKeyboardMarkup;

import java.util.List;
import java.util.Map;

/** Постоянное меню под полем ввода. Нажатие присылает текст кнопки, роутер превращает его в команду. */
public final class MenuButtons {

    public static final String SUBSCRIBE = "➕ Новая подписка";
    public static final String SUBSCRIPTIONS = "📋 Мои подписки";
    public static final String FAVORITES = "⭐ Избранное";
    public static final String HELP = "❓ Помощь";

    private static final Map<String, String> COMMANDS = Map.of(
            SUBSCRIBE, "/subscribe",
            SUBSCRIPTIONS, "/subscriptions",
            FAVORITES, "/favorites",
            HELP, "/help");

    private MenuButtons() {
    }

    /** Команда для текста кнопки меню или null, если текст не из меню. */
    public static String commandFor(String text) {
        return text == null ? null : COMMANDS.get(text.trim());
    }

    public static ReplyKeyboardMarkup markup() {
        return new ReplyKeyboardMarkup(List.of(
                List.of(new KeyboardButton(SUBSCRIBE), new KeyboardButton(SUBSCRIPTIONS)),
                List.of(new KeyboardButton(FAVORITES), new KeyboardButton(HELP))),
                true, true);
    }
}
