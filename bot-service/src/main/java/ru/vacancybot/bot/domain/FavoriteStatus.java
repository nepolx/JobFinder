package ru.vacancybot.bot.domain;

/** Пометка к избранной вакансии. */
public enum FavoriteStatus implements Coded {
    SAVED("saved", "Сохранена"),
    APPLIED("applied", "Откликнулся"),
    WAITING("waiting", "Жду ответа");

    private final String code;
    private final String label;

    FavoriteStatus(String code, String label) {
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
