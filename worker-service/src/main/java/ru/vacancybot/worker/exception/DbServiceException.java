package ru.vacancybot.worker.exception;

public class DbServiceException extends RuntimeException {

    private final Integer httpStatus;
    private final boolean retryable;

    public DbServiceException(String message, Integer httpStatus, boolean retryable) {
        super(message);
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public DbServiceException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.httpStatus = null;
        this.retryable = retryable;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }
}