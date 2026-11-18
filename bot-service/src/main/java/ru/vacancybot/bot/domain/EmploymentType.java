package ru.vacancybot.bot.domain;

/**
 * Тип занятости. Коды взяты из документации как есть.
 * ВНИМАНИЕ: у «Сезонной работы» в документации код rotation — такой же, как у «Вахтового метода» в графике.
 * Если это опечатка, поменяйте код здесь в одном месте.
 */
public enum EmploymentType implements Coded {
    SEASONAL("rotation", "Сезонная работа"),
    REMOTE("remote", "Дистанционная (удалённая) работа"),
    TEMPORARY("temp", "Временная работа"),
    INTERNSHIP("intern", "Стажировка");

    private final String code;
    private final String label;

    EmploymentType(String code, String label) {
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
