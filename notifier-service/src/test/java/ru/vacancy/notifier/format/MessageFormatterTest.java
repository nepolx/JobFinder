package ru.vacancy.notifier.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.vacancy.notifier.model.ChangeEvent;
import ru.vacancy.notifier.model.ChangeType;
import ru.vacancy.notifier.model.Vacancy;

class MessageFormatterTest {

    static final String NB = "\u00a0";
    static final UUID VID = UUID.fromString("3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d");

    static Vacancy vacancy() {
        return new Vacancy(VID, "Python-разработчик", "ООО Ромашка", "Москва",
                100000, 150000, "Полный день", "https://trudvsem.ru/vacancy/card/1");
    }

    static ChangeEvent event(ChangeType type, Map<String, List<Object>> diff) {
        return new ChangeEvent(1, VID, type, OffsetDateTime.parse("2026-11-08T12:00:00Z"), diff);
    }

    static ChangeEvent event(ChangeType type) {
        return event(type, Map.of());
    }

    // ---- деньги ----

    @Test
    void moneyHasThousandSeparators() {
        assertEquals("1" + NB + "234" + NB + "567" + NB + "₽", MessageFormatter.formatMoney(1234567L));
        assertEquals("999" + NB + "₽", MessageFormatter.formatMoney(999L));
        assertEquals("не указана", MessageFormatter.formatMoney(null));
    }

    @Test
    void salaryVariants() {
        assertEquals("не указана", MessageFormatter.formatSalary(null, null));
        assertEquals("от 50" + NB + "000" + NB + "₽", MessageFormatter.formatSalary(50000, null));
        assertEquals("до 70" + NB + "000" + NB + "₽", MessageFormatter.formatSalary(null, 70000));
        assertEquals("50" + NB + "000" + NB + "₽", MessageFormatter.formatSalary(50000, 50000));
        assertEquals("50" + NB + "000 – 70" + NB + "000" + NB + "₽", MessageFormatter.formatSalary(50000, 70000));
    }

    // ---- сообщения ----

    @Test
    void createdContainsKeyFactsAndLink() {
        String text = MessageFormatter.format(event(ChangeType.CREATED), vacancy());
        assertTrue(text.contains("Новая вакансия"));
        assertTrue(text.contains("<b>Python-разработчик</b>"));
        assertTrue(text.contains("ООО Ромашка · Москва"));
        assertTrue(text.contains("График: Полный день"));
        assertTrue(text.contains("<a href=\"https://trudvsem.ru/vacancy/card/1\">"));
    }

    @Test
    void updatedShowsOldAndNewValues() {
        Map<String, List<Object>> diff = new LinkedHashMap<>();
        diff.put("salary_min", List.of(50000, 70000));
        diff.put("schedule", List.of("Полный день", "Гибкий"));
        String text = MessageFormatter.format(event(ChangeType.UPDATED, diff), vacancy());
        assertTrue(text.contains("Что изменилось"));
        assertTrue(text.contains("Зарплата от: 50" + NB + "000" + NB + "₽ → 70" + NB + "000" + NB + "₽"), text);
        assertTrue(text.contains("График: Полный день → Гибкий"), text);
    }

    @Test
    void updatedSalaryChangeFromNullShowsNotSpecified() {
        java.util.List<Object> pair = new java.util.ArrayList<>();
        pair.add(null);
        pair.add(80000);
        String text = MessageFormatter.format(event(ChangeType.UPDATED, Map.of("salary_max", pair)), vacancy());
        assertTrue(text.contains("Зарплата до: не указана → 80" + NB + "000" + NB + "₽"), text);
    }

    @Test
    void updatedLongTextFieldsAreOnlyMentioned() {
        Map<String, List<Object>> diff =
                Map.of("requirements", List.of("очень длинный старый текст", "очень длинный новый текст"));
        String text = MessageFormatter.format(event(ChangeType.UPDATED, diff), vacancy());
        assertTrue(text.contains("Изменились требования"));
        assertFalse(text.contains("очень длинный"));
    }

    @Test
    void updatedWithOnlyUnknownFieldsStillMakesSense() {
        String text = MessageFormatter.format(
                event(ChangeType.UPDATED, Map.of("internal_flag", List.of(0, 1))), vacancy());
        assertTrue(text.contains("Условия вакансии обновлены"));
        assertFalse(text.contains("internal_flag"));
    }

    @Test
    void closedHasNoLinkAndSaysSo() {
        String text = MessageFormatter.format(event(ChangeType.CLOSED), vacancy());
        assertTrue(text.contains("закрыта"));
        assertFalse(text.contains("<a "));
    }

    // ---- безопасность и границы ----

    @Test
    void htmlInUserDataIsEscaped() {
        Vacancy v = new Vacancy(VID, "<b>R&D</b> <script>", "ООО \"A&B\"", "Москва", null, null, null, null);
        String text = MessageFormatter.format(event(ChangeType.CREATED), v);
        assertFalse(text.contains("<script>"));
        assertTrue(text.contains("&lt;script&gt;"));
        assertTrue(text.contains("&amp;"));
    }

    @Test
    void onlyHttpLinksAreAllowed() {
        Vacancy v = new Vacancy(VID, "Job", "Org", "City", null, null, null, "javascript:alert(1)");
        assertFalse(MessageFormatter.format(event(ChangeType.CREATED), v).contains("<a "));
    }

    @Test
    void quotesInUrlCannotBreakAttribute() {
        Vacancy v = new Vacancy(VID, "Job", "Org", "City", null, null, null, "https://x.ru/?q=\"><b>hack");
        String text = MessageFormatter.format(event(ChangeType.CREATED), v);
        assertFalse(text.contains("\"><b>"));
    }

    @Test
    void messageNeverExceedsTelegramLimit() {
        String nasty = "&".repeat(5000); // при экранировании каждый & становится &amp;
        Map<String, List<Object>> diff = new LinkedHashMap<>();
        for (String f : List.of("schedule", "employer_name", "region_name", "job_name", "experience_years")) {
            diff.put(f, List.of(nasty, nasty));
        }
        diff.put("salary_min", List.of(1, 2));
        diff.put("salary_max", List.of(3, 4));
        diff.put("duty", List.of("a", "b"));
        Vacancy v = new Vacancy(VID, nasty, nasty, nasty, null, null, nasty, "https://x.ru/" + "&".repeat(3000));

        for (ChangeType type : ChangeType.values()) {
            String text = MessageFormatter.format(event(type, diff), v);
            assertTrue(text.length() <= MessageFormatter.MAX_MESSAGE_LEN, type + ": " + text.length());
            assertFalse(text.contains("&am…"), "сущность обрезана посередине"); // проверяем, что & не рвётся
        }
    }

    @Test
    void fitDoesNotSplitSurrogatePairs() {
        String emoji = "😀".repeat(100);
        String fitted = MessageFormatter.fit(emoji, 11);
        assertFalse(Character.isHighSurrogate(fitted.charAt(fitted.length() - 2)), fitted);
        assertTrue(fitted.length() <= 11);
    }
}
