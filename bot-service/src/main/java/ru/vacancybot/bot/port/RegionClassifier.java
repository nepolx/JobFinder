package ru.vacancybot.bot.port;

import ru.vacancybot.bot.domain.Region;

import java.util.List;
import java.util.Optional;

/** Контракт с сервисом напарника: классификатор регионов trudvsem.ru (из кэша, ≤ 200 мс). */
public interface RegionClassifier {

    /** Поиск по названию без учёта регистра и лишних пробелов. */
    Optional<Region> findByName(String name);

    Optional<Region> findByCode(String code);

    /** Регионы для кнопок быстрого выбора (Москва, Санкт-Петербург). */
    List<Region> popular();
}
