package ru.vacancy.notifier.service;

/** Итог обработки одного события. retryLater > 0: событие нужно обработать ещё раз позже. */
public record HandleResult(int sent, int blocked, int failed, int retryLater) {

    public static final HandleResult EMPTY = new HandleResult(0, 0, 0, 0);
}
