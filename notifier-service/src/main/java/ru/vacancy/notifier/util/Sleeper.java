package ru.vacancy.notifier.util;

@FunctionalInterface
public interface Sleeper {

    void sleep(double seconds);

    Sleeper REAL = seconds -> {
        try {
            Thread.sleep((long) Math.ceil(seconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("прервано во время ожидания", e);
        }
    };
}
