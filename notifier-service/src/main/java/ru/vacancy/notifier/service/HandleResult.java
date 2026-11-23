package ru.vacancy.notifier.service;

public record HandleResult(int sent, int blocked, int failed, int retryLater) {

    public static final HandleResult EMPTY = new HandleResult(0, 0, 0, 0);
}
