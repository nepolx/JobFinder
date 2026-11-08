package ru.vacancybot.bot.domain;

import java.util.Optional;

/** Значение с кодом для callback_data/БД и подписью для пользователя. */
public interface Coded {

    String code();

    String label();

    static <E extends Enum<E> & Coded> Optional<E> byCode(Class<E> type, String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (E e : type.getEnumConstants()) {
            if (e.code().equals(code)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    static <E extends Enum<E> & Coded> String labelOrCode(Class<E> type, String code) {
        return byCode(type, code).map(Coded::label).orElse(code);
    }
}
