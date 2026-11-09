package ru.vacancybot.bot.stub;

import org.springframework.stereotype.Service;
import ru.vacancybot.bot.domain.Region;
import ru.vacancybot.bot.port.RegionClassifier;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Заглушка: несколько регионов с кодами в формате trudvsem.ru. Нужна всегда: эндпоинта регионов в db-service пока нет. */
@Service
public class StubRegionClassifier implements RegionClassifier {

    public static final Region MOSCOW = new Region("7700000000000", "Москва");
    public static final Region SPB = new Region("7800000000000", "Санкт-Петербург");

    private final List<Region> all = List.of(
            MOSCOW, SPB,
            new Region("4700000000000", "Ленинградская область"),
            new Region("5400000000000", "Новосибирская область"),
            new Region("6600000000000", "Свердловская область"),
            new Region("1600000000000", "Республика Татарстан"));

    @Override
    public Optional<Region> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = normalize(name);
        return all.stream().filter(r -> normalize(r.name()).equals(wanted)).findFirst();
    }

    @Override
    public Optional<Region> findByCode(String code) {
        return all.stream().filter(r -> r.code().equals(code)).findFirst();
    }

    @Override
    public List<Region> popular() {
        return List.of(MOSCOW, SPB);
    }

    private static String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
