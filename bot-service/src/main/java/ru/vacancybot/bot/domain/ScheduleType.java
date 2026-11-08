package ru.vacancybot.bot.domain;

/** График работы. Коды совпадают с vacancies.schedule_code. */
public enum ScheduleType implements Coded {
    ROTATION("rotation", "Вахтовый метод"),
    FLEX("flex", "Режим гибкого рабочего времени"),
    SHIFT("shift", "Сменная работа"),
    IRREGULAR("irregular", "Ненормированный рабочий день"),
    PART("part", "Неполный рабочий день/ неполная рабочая неделя"),
    FULL("full", "Полный рабочий день");

    private final String code;
    private final String label;

    ScheduleType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String label() {
        return label;
    }
}
