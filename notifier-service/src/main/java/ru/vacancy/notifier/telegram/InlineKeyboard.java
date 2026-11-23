package ru.vacancy.notifier.telegram;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Inline-кнопки под сообщением. Нажатия на них принимает bot-service (тот же бот, webhook),
 * поэтому {@code callback_data} должен совпадать с контрактом из bot-service/README.md.
 */
public record InlineKeyboard(List<List<Button>> rows) {

    /** 👎: бот удаляет уведомление. */
    public static final String SKIP_PREFIX = "vac_skip_";
    /** 👍: бот кладёт вакансию в избранное. */
    public static final String FAVORITE_PREFIX = "fav_add_";

    private static final int MAX_CALLBACK_BYTES = 64; // лимит Telegram

    public record Button(String text, String callbackData) {

        public Button {
            if (callbackData.getBytes(StandardCharsets.UTF_8).length > MAX_CALLBACK_BYTES) {
                throw new IllegalArgumentException("callback_data длиннее 64 байт: " + callbackData);
            }
        }
    }

    /** Кнопки под уведомлением о новой вакансии: [👎 Не интересно] [👍 В избранное]. */
    public static InlineKeyboard newVacancyActions(UUID vacancyId) {
        return new InlineKeyboard(List.of(List.of(
                new Button("👎 Не интересно", SKIP_PREFIX + vacancyId),
                new Button("👍 В избранное", FAVORITE_PREFIX + vacancyId))));
    }

    /** Значение поля reply_markup для Telegram Bot API. */
    public Map<String, Object> toPayload() {
        List<List<Map<String, Object>>> keyboard = rows.stream()
                .map(row -> row.stream().map(b -> {
                    Map<String, Object> button = new LinkedHashMap<>();
                    button.put("text", b.text());
                    button.put("callback_data", b.callbackData());
                    return button;
                }).toList())
                .toList();
        Map<String, Object> markup = new LinkedHashMap<>();
        markup.put("inline_keyboard", keyboard);
        return markup;
    }
}
