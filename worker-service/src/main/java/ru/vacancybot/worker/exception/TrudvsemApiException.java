package ru.vacancybot.worker.exception;

import lombok.Getter;

/**
 * Ошибка при обращении к API trudvsem.ru.
 *
 * <p>{@link #isRetryable()} подсказывает слою повторов, есть ли смысл повторять запрос:
 * таймауты, сетевые сбои, 5xx и 429 да; ошибки разбора и 4xx нет.
 */
@Getter
public class TrudvsemApiException extends RuntimeException {
    private final Integer httpStatus;
    private final boolean retryable;

    public TrudvsemApiException(String message) {
        this(message, (Integer) null, false);
    }

    public TrudvsemApiException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public TrudvsemApiException(String message, Integer httpStatus, boolean retryable) {
        super(message);
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public TrudvsemApiException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.httpStatus = null;
        this.retryable = retryable;
    }
}