package ru.vacancybot.bot.telegram;

/** Ошибка вызова Telegram Bot API. retryAfterSeconds заполнен при 429 (лимит отправки). */
public class TelegramApiException extends RuntimeException {

    private final int errorCode;
    private final Integer retryAfterSeconds;

    public TelegramApiException(int errorCode, String description, Integer retryAfterSeconds) {
        super("Telegram API error " + errorCode + ": " + description);
        this.errorCode = errorCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
