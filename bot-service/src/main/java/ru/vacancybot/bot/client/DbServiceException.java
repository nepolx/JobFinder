package ru.vacancybot.bot.client;

/** db-service ответил ошибкой (status 4xx/5xx) или недоступен (status 0). */
public class DbServiceException extends RuntimeException {

    private final int status;

    public DbServiceException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
