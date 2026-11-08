package ru.vacancy.notifier.retry;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleUnaryOperator;

import ru.vacancy.notifier.telegram.RetryAfterException;
import ru.vacancy.notifier.telegram.TemporaryException;
import ru.vacancy.notifier.util.Sleeper;

/**
 * Повторяет действие при TemporaryException.
 * <ul>
 *   <li>RetryAfterException: ждём ровно столько, сколько просит Telegram;</li>
 *   <li>TemporaryException: экспоненциальная задержка с jitter;</li>
 *   <li>PermanentException и любые другие исключения: сразу наружу, повторять бессмысленно.</li>
 * </ul>
 * После maxAttempts последняя ошибка пробрасывается. Объект неизменяемый и потокобезопасный.
 */
public final class RetryExecutor {

    private final int maxAttempts;
    private final double baseDelay;
    private final double maxDelay;
    private final Sleeper sleeper;
    private final DoubleUnaryOperator jitter;

    public RetryExecutor(int maxAttempts, double baseDelay, double maxDelay, Sleeper sleeper, DoubleUnaryOperator jitter) {
        this.maxAttempts = maxAttempts;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.sleeper = sleeper;
        this.jitter = jitter;
    }

    public static RetryExecutor withDefaults(int maxAttempts) {
        return new RetryExecutor(maxAttempts, 1.0, 30.0, Sleeper.REAL,
                d -> d * (0.5 + ThreadLocalRandom.current().nextDouble() * 0.5));
    }

    /** Задержка после неудачной попытки №attempt (с 1): base, 2*base, 4*base ... не больше cap. */
    public static double computeBackoff(int attempt, double base, double cap) {
        return Math.min(cap, base * Math.pow(2, attempt - 1));
    }

    public void run(Runnable action) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                action.run();
                return;
            } catch (RetryAfterException e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                sleeper.sleep(e.retryAfter());
            } catch (TemporaryException e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                sleeper.sleep(jitter.applyAsDouble(computeBackoff(attempt, baseDelay, maxDelay)));
            }
        }
    }
}
