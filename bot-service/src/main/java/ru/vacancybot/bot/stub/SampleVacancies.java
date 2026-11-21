package ru.vacancybot.bot.stub;

import ru.vacancybot.bot.domain.Vacancy;

/** Примеры вакансий из шаблонов уведомлений в документации. */
public final class SampleVacancies {

    public static final Vacancy NEW_VACANCY = new Vacancy(
            "00000000-0000-0000-0000-000000123456",
            "Junior Java Developer", "ООО «Цифровые Решения»", "Санкт-Петербург",
            120_000, null, "Полный рабочий день",
            "https://trudvsem.ru/vacancy/card/123456");

    public static final Vacancy CHANGED_VACANCY = new Vacancy(
            "00000000-0000-0000-0000-000000789012",
            "Java Developer", "ООО «Технологии»", "Москва",
            130_000, null, "Полный рабочий день",
            "https://trudvsem.ru/vacancy/card/789012");

    public static final java.util.List<Vacancy> FEED = java.util.List.of(
            NEW_VACANCY,
            CHANGED_VACANCY,
            new Vacancy("00000000-0000-0000-0000-000000000003", "Backend-разработчик (Kotlin)", "ООО «Вектор»",
                    "Москва", 180_000, 250_000, "Удалённая работа", "https://trudvsem.ru/vacancy/card/3"),
            new Vacancy("00000000-0000-0000-0000-000000000004", "QA-инженер", "АО «Тестсофт»",
                    "Санкт-Петербург", 90_000, null, "Полный рабочий день", "https://trudvsem.ru/vacancy/card/4"),
            new Vacancy("00000000-0000-0000-0000-000000000005", "Стажёр-аналитик данных", "ООО «Аналитика+»",
                    "Москва", null, null, "Гибкий график", "https://trudvsem.ru/vacancy/card/5"));

    private SampleVacancies() {
    }
}
