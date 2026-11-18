package ru.vacancy.notifier.telegram;

import java.util.Map;

/** Превращает ответ Bot API в исключение нужного типа. Чистая функция, легко тестируется. */
public final class TelegramResponses {

    private TelegramResponses() {
    }

    public static void check(int status, Map<String, ?> body) {
        Map<String, ?> b = body == null ? Map.of() : body;
        Object rawDescription = b.get("description");
        String description = rawDescription == null ? "" : String.valueOf(rawDescription);
        if (status == 200 && Boolean.TRUE.equals(b.get("ok"))) {
            return;
        }
        if (status == 429) {
            throw new RetryAfterException(retryAfter(b));
        }
        if (status == 403) { // бот заблокирован, пользователь удалён и т.п.
            throw new BotBlockedException(description.isEmpty() ? "forbidden" : description);
        }
        if (status == 400) {
            if (description.toLowerCase().contains("chat not found")) {
                throw new BotBlockedException(description);
            }
            throw new PermanentException(description.isEmpty() ? "bad request" : description);
        }
        if (status == 401) {
            // Неверный токен: ошибка конфигурации, а не вина получателя. Считаем временной,
            // чтобы не пометить всех пользователей как «не доставлено».
            throw new TemporaryException("unauthorized: проверьте TELEGRAM_BOT_TOKEN");
        }
        if (status >= 500) {
            throw new TemporaryException("telegram " + status + ": " + description);
        }
        throw new PermanentException("unexpected status " + status + ": " + description);
    }

    private static double retryAfter(Map<String, ?> body) {
        if (body.get("parameters") instanceof Map<?, ?> params && params.get("retry_after") instanceof Number n) {
            return n.doubleValue();
        }
        return 1.0;
    }
}
