package ru.vacancybot.bot.port;

/** Источник подборки недоступен (например, в db-service ещё нет нужного эндпоинта). */
public class FeedUnavailableException extends RuntimeException {

    public FeedUnavailableException(String message) {
        super(message);
    }
}
