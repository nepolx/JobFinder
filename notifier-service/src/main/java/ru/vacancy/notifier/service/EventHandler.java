package ru.vacancy.notifier.service;

import ru.vacancy.notifier.model.ChangeEvent;

/** Что нужно обработчику сообщений от бизнес-логики (и что можно подменить в тестах). */
public interface EventHandler {

    HandleResult handle(ChangeEvent event);

    /** Окончательно помечает недоставленные уведомления события как failed. */
    int giveUp(ChangeEvent event);
}
