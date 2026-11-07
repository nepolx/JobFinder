package ru.vacancybot.common.constant;

public final class MessagingConstants {
    public static final String EXCHANGE = "vacancy.events";
    public static final String NEW_VACANCY_QUEUE = "vacancy.new";
    public static final String NEW_VACANCY_KEY = "vacancy.new";
    public static final String CHANGED_VACANCY_QUEUE = "vacancy.changed";
    public static final String CHANGED_VACANCY_KEY = "vacancy.changed";

    private MessagingConstants() {}
}
