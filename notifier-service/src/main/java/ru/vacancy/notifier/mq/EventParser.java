package ru.vacancy.notifier.mq;

import ru.vacancy.notifier.model.ChangeEvent;
import ru.vacancy.notifier.model.InvalidEventException;

/** Превращает тело сообщения в событие. */
public interface EventParser {

    /** @throws InvalidEventException если тело не соответствует контракту */
    ChangeEvent parse(byte[] body);
}
