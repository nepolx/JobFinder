package ru.vacancy.notifier.mq;

import ru.vacancy.notifier.model.ChangeEvent;
import ru.vacancy.notifier.model.InvalidEventException;


public interface EventParser {

    /** @throws InvalidEventException  */
    ChangeEvent parse(byte[] body);
}
