package ru.vacancybot.bot.domain;

/** Опыт работы. Код уходит в SubscriptionDto.experience, диапазон лет в БД считает db-service. */
public enum Experience implements Coded {
    NONE("none", "Без опыта"),
    Y1_3("1_3", "1–3 года"),
    Y3_6("3_6", "3–6 лет"),
    Y6_PLUS("6_plus", "Более 6 лет");

    private final String code;
    private final String label;

    Experience(String code, String label) {
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
