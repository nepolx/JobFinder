package ru.vacancy.notifier.util;

/** Пауза, вынесенная в интерфейс, чтобы в тестах не ждать по-настоящему. */
@FunctionalInterface
public interface Sleeper {

    void sleep(double seconds);

    /** Настоящая пауза потока (в виртуальных потоках не блокирует системный поток). */
    Sleeper REAL = seconds -> {
        try {
            Thread.sleep((long) Math.ceil(seconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("прервано во время ожидания", e);
        }
    };
}
