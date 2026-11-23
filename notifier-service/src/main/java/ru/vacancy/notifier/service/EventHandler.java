package ru.vacancy.notifier.service;

import ru.vacancy.notifier.model.ChangeEvent;

public interface EventHandler {

    HandleResult handle(ChangeEvent event);

    int giveUp(ChangeEvent event);
}
