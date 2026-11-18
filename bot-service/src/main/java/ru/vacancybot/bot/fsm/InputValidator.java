package ru.vacancybot.bot.fsm;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Проверка пользовательского ввода мастера подписки. Чистые функции, без зависимостей. */
public final class InputValidator {

    public static final int MAX_TITLE = 100;
    public static final int MAX_KEYWORDS = 200;

    public static final String TITLE_HINT = "Название должно быть от 1 до " + MAX_TITLE + " символов.";
    public static final String SALARY_HINT = "Введите целое число, например: 80000";
    public static final String KEYWORDS_HINT = "Введите от 1 до " + MAX_KEYWORDS + " символов, ключевые слова через запятую.";

    /** error == null — ввод корректен; value может быть null (например, зарплата 0 = пропуск). */
    public record Result<T>(T value, String error) {

        public boolean ok() {
            return error == null;
        }

        static <T> Result<T> ok(T value) {
            return new Result<>(value, null);
        }

        static <T> Result<T> fail(String error) {
            return new Result<>(null, error);
        }
    }

    private InputValidator() {
    }

    public static Result<String> title(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty() || t.length() > MAX_TITLE) {
            return Result.fail(TITLE_HINT);
        }
        return Result.ok(t);
    }

    /** Целое число >= 0; пробелы внутри допускаются («80 000»). 0 приравнивается к пропуску (value == null). */
    public static Result<Integer> salary(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[\\s\\u00A0\\u202F]", "");
        if (!digits.matches("\\d{1,9}")) {
            return Result.fail(SALARY_HINT);
        }
        int value = Integer.parseInt(digits);
        return Result.ok(value == 0 ? null : value);
    }

    /** Ключевые слова через запятую: пробелы нормализуются, пустые элементы отбрасываются, до 200 символов. */
    public static Result<String> keywords(String raw) {
        String normalized = raw == null ? "" : Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        if (normalized.isEmpty() || normalized.length() > MAX_KEYWORDS) {
            return Result.fail(KEYWORDS_HINT);
        }
        return Result.ok(normalized);
    }
}
