package ru.vacancybot.db.mapper;

import ru.vacancybot.common.dto.SubscriptionDto;
import ru.vacancybot.db.entity.Subscription;

/**
 * Преобразование между SubscriptionDto (общий контракт) и Subscription (таблица).
 *
 * Соответствие полей:
 *   region     <-> region_code
 *   schedule   <-> schedule_code   (rotation, flex, shift, irregular, part, full)
 *   employment <-> employment_code
 *   experience (код кнопки) <-> experience_from / experience_to (числа):
 *       none    -> 0..0      (без опыта)
 *       1_3     -> 1..3
 *       3_6     -> 3..6
 *       6_plus  -> 6..NULL   (более 6 лет)
 *   Пустое значение = опыт не задан (оба поля NULL).
 */
public final class SubscriptionMapper {

    private SubscriptionMapper() {
    }

    public static SubscriptionDto toDto(Subscription s, Long telegramUserId) {
        return new SubscriptionDto(
                s.getId() == null ? null : s.getId().longValue(),
                telegramUserId,
                s.getTitle(),
                s.getRegionCode(),
                s.getSalaryMin(),
                experienceToCode(s.getExperienceFrom(), s.getExperienceTo()),
                s.getScheduleCode(),
                s.getEmploymentCode(),
                s.getKeywords(),
                s.isActive());
    }

    /** Копирует параметры поиска из DTO в сущность (id, пользователь и active не трогает). */
    public static void apply(SubscriptionDto dto, Subscription s) {
        s.setTitle(blankToNull(dto.title()));
        s.setRegionCode(blankToNull(dto.region()));
        // по спецификации зарплата 0 равна пропуску
        s.setSalaryMin(dto.salaryMin() == null || dto.salaryMin() <= 0 ? null : dto.salaryMin());
        s.setScheduleCode(blankToNull(dto.schedule()));
        s.setEmploymentCode(blankToNull(dto.employment()));
        s.setKeywords(blankToNull(dto.keywords()));

        Integer[] range = codeToExperience(dto.experience());
        s.setExperienceFrom(range[0]);
        s.setExperienceTo(range[1]);
    }

    static Integer[] codeToExperience(String code) {
        String v = blankToNull(code);
        if (v == null) {
            return new Integer[]{null, null};
        }
        return switch (v) {
            case "none" -> new Integer[]{0, 0};
            case "1_3" -> new Integer[]{1, 3};
            case "3_6" -> new Integer[]{3, 6};
            case "6_plus" -> new Integer[]{6, null};
            default -> throw new IllegalArgumentException(
                    "Неизвестный код опыта: \"" + code + "\" (допустимо: none, 1_3, 3_6, 6_plus)");
        };
    }

    static String experienceToCode(Integer from, Integer to) {
        if (from == null && to == null) {
            return null;
        }
        if (from != null && from == 0 && to != null && to == 0) {
            return "none";
        }
        if (from != null && from == 1 && to != null && to == 3) {
            return "1_3";
        }
        if (from != null && from == 3 && to != null && to == 6) {
            return "3_6";
        }
        if (from != null && from == 6 && to == null) {
            return "6_plus";
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}