package ru.vacancybot.bot;

import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.domain.VacancyChange;
import ru.vacancybot.bot.stub.SampleVacancies;
import ru.vacancybot.bot.view.MessageFormatter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageFormatterTest {

    private final MessageFormatter fmt = new MessageFormatter("");

    @Test
    void newVacancy_matchesTemplateFromSpecification() {
        assertEquals("Найдена новая вакансия по вашей подписке!\n"
                        + "Должность: Junior Java Developer\n"
                        + "Работодатель: ООО «Цифровые Решения»\n"
                        + "Регион: Санкт-Петербург\n"
                        + "Зарплата: от 120 000 руб.\n"
                        + "График: Полный рабочий день\n"
                        + "Ссылка на вакансию: https://trudvsem.ru/vacancy/card/123456",
                fmt.newVacancy(SampleVacancies.NEW_VACANCY));
    }

    @Test
    void vacancyChanged_matchesTemplateFromSpecification() {
        var change = new VacancyChange(SampleVacancies.CHANGED_VACANCY,
                List.of(new VacancyChange.FieldChange("Зарплата", "100 000 руб.", "130 000 руб.")), false);

        assertEquals("Изменение в избранной вакансии!\n"
                        + "Должность: Java Developer\n"
                        + "Работодатель: ООО «Технологии»\n"
                        + "Что изменилось:\n"
                        + "• Зарплата: была 100 000 руб. ➔ стала 130 000 руб.\n"
                        + "• Статус: Открыта\n"
                        + "Ссылка: https://trudvsem.ru/vacancy/card/789012",
                fmt.vacancyChanged(change));
    }

    @Test
    void closedVacancy_isReportedAsClosed() {
        var change = new VacancyChange(SampleVacancies.CHANGED_VACANCY, List.of(), true);
        assertTrue(fmt.vacancyChanged(change).contains("• Статус: Закрыта"));
    }

    @Test
    void money_groupsThousands() {
        assertEquals("0", MessageFormatter.money(0));
        assertEquals("999", MessageFormatter.money(999));
        assertEquals("1 000", MessageFormatter.money(1000));
        assertEquals("80 000", MessageFormatter.money(80_000));
        assertEquals("1 234 567", MessageFormatter.money(1_234_567));
    }

    @Test
    void salary_coversAllCombinations() {
        assertEquals("от 80 000 руб.", MessageFormatter.salary(80_000, null));
        assertEquals("от 80 000 руб.", MessageFormatter.salary(80_000, 0));
        assertEquals("до 90 000 руб.", MessageFormatter.salary(null, 90_000));
        assertEquals("от 80 000 до 120 000 руб.", MessageFormatter.salary(80_000, 120_000));
        assertEquals("не указана", MessageFormatter.salary(null, null));
        assertEquals("не указана", MessageFormatter.salary(0, 0));
    }

    @Test
    void helpShowsSupportContactOnlyWhenConfigured() {
        assertFalse(fmt.help().contains("Поддержка"));
        assertTrue(new MessageFormatter(" @help_me ").help().endsWith("Поддержка: @help_me"));
    }

    @Test
    void welcomeListsAllCommands() {
        for (String command : List.of("/subscribe", "/subscriptions", "/favorites", "/help")) {
            assertTrue(fmt.welcome().contains(command), command);
        }
    }
}
