package ru.vacancy.notifier.telegram;

/** Telegram просит подождать (HTTP 429). */
public class RetryAfterException extends TemporaryException {

    private final double retryAfter;

    public RetryAfterException(double retryAfter) {
        super("flood control: retry after " + retryAfter + "s");
        this.retryAfter = retryAfter;
    }

    public double retryAfter() {
        return retryAfter;
    }
}
