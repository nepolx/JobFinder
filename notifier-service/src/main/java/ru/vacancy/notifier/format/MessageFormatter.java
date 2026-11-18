package ru.vacancy.notifier.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.vacancy.notifier.model.ChangeEvent;
import ru.vacancy.notifier.model.ChangeType;
import ru.vacancy.notifier.model.Vacancy;

/** Формирование текста уведомления (Telegram HTML, лимит 4096 символов). */
public final class MessageFormatter {

    public static final int MAX_MESSAGE_LEN = 4096;

    private static final String NBSP = "\u00a0";
    private static final int URL_MAX_ESCAPED = 800;

    private static final Map<String, String> FIELD_LABELS = Map.of(
            "salary_min", "Зарплата от",
            "salary_max", "Зарплата до",
            "schedule", "График",
            "experience_years", "Опыт (лет)",
            "employer_name", "Работодатель",
            "region_name", "Регион",
            "job_name", "Название");
    private static final Set<String> MONEY_FIELDS = Set.of("salary_min", "salary_max");
    // Длинные тексты в сообщении не показываем, только факт изменения.
    private static final Map<String, String> TEXT_FIELDS = Map.of(
            "duty", "обязанности",
            "requirements", "требования");

    private MessageFormatter() {
    }

    public static String format(ChangeEvent event, Vacancy vacancy) {
        List<String> parts = new ArrayList<>();
        parts.add(header(event.type()));
        parts.add("");
        parts.add("<b>" + fit(vacancy.jobName(), 150) + "</b>");
        parts.add(fit(vacancy.employerName(), 80) + " · " + fit(vacancy.regionName(), 80));

        if (event.type() == ChangeType.CLOSED) {
            parts.add("Вакансия закрыта, отклик больше невозможен.");
            return String.join("\n", parts);
        }

        parts.add("Зарплата: " + formatSalary(vacancy.salaryMin(), vacancy.salaryMax()));
        if (vacancy.schedule() != null && !vacancy.schedule().isBlank()) {
            parts.add("График: " + fit(vacancy.schedule(), 100));
        }

        if (event.type() == ChangeType.UPDATED) {
            parts.add("");
            parts.add("<b>Что изменилось:</b>");
            List<String> changes = formatChanges(event.diff());
            if (changes.isEmpty()) {
                parts.add("• Условия вакансии обновлены");
            } else {
                parts.addAll(changes);
            }
        }

        String link = link(vacancy.vacUrl());
        if (link != null) {
            parts.add("");
            parts.add(link);
        }
        return String.join("\n", parts);
    }

    private static String header(ChangeType type) {
        return switch (type) {
            case CREATED -> "🆕 Новая вакансия по вашей подписке";
            case UPDATED -> "✏️ Вакансия из избранного изменилась";
            case CLOSED -> "🚫 Вакансия из избранного закрыта";
        };
    }

    public static String formatMoney(Long value) {
        if (value == null) {
            return "не указана";
        }
        return groupThousands(value) + NBSP + "₽";
    }

    public static String formatSalary(Integer min, Integer max) {
        if (min == null && max == null) {
            return "не указана";
        }
        if (min != null && max != null && !min.equals(max)) {
            return groupThousands(min) + " – " + formatMoney(max.longValue());
        }
        if (min == null) {
            return "до " + formatMoney(max.longValue());
        }
        if (max == null) {
            return "от " + formatMoney(min.longValue());
        }
        return formatMoney(min.longValue()); // min == max
    }

    private static String groupThousands(long value) {
        String digits = Long.toString(Math.abs(value));
        StringBuilder sb = new StringBuilder(value < 0 ? "-" : "");
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                sb.append(NBSP);
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    private static String formatValue(String field, Object value) {
        if (MONEY_FIELDS.contains(field)) {
            return formatMoney(value instanceof Number n ? n.longValue() : null);
        }
        if (value == null || value.toString().isEmpty()) {
            return "не указано";
        }
        return fit(value.toString(), 80);
    }

    private static List<String> formatChanges(Map<String, List<Object>> diff) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<Object>> entry : diff.entrySet()) {
            String field = entry.getKey();
            List<Object> pair = entry.getValue();
            if (TEXT_FIELDS.containsKey(field)) {
                lines.add("• Изменились " + TEXT_FIELDS.get(field));
            } else if (FIELD_LABELS.containsKey(field)) {
                lines.add("• " + FIELD_LABELS.get(field) + ": "
                        + formatValue(field, pair.get(0)) + " → " + formatValue(field, pair.get(1)));
            }
            // неизвестные поля молча пропускаем: их формат нам не обещан
        }
        return lines;
    }

    private static String link(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return null;
        }
        String escaped = escape(url, true);
        if (escaped.length() > URL_MAX_ESCAPED) {
            return null;
        }
        return "<a href=\"" + escaped + "\">Открыть вакансию</a>";
    }

    /** Экранирует текст для Telegram HTML; результат не длиннее {@code limit} символов. */
    static String fit(String text, int limit) {
        String raw = text == null ? "" : text.strip();
        if (raw.length() > limit) {
            raw = cut(raw, limit - 1).stripTrailing() + "…";
        }
        String escaped = escape(raw, false);
        while (escaped.length() > limit) { // экранирование (& -> &amp;) удлиняет строку
            raw = cut(raw, Math.max(raw.length() - 2, 0)).stripTrailing() + "…";
            escaped = escape(raw, false);
        }
        return escaped;
    }

    /** Обрезка до end символов, не разрывая суррогатную пару (эмодзи). */
    private static String cut(String s, int end) {
        int e = Math.min(end, s.length());
        if (e > 0 && Character.isHighSurrogate(s.charAt(e - 1))) {
            e--;
        }
        return s.substring(0, e);
    }

    static String escape(String s, boolean quotes) {
        String r = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        if (quotes) {
            r = r.replace("\"", "&quot;").replace("'", "&#x27;");
        }
        return r;
    }
}
