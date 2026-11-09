package ru.vacancybot.bot.fsm;

/** Состояния FSM мастера создания подписки. Порядок констант = порядок вопросов. */
public enum DialogState {
    IDLE,
    SEARCH_TITLE,
    SEARCH_REGION,
    SEARCH_SALARY,
    SEARCH_EXPERIENCE,
    SEARCH_SCHEDULE,
    SEARCH_EMPLOYMENT,
    SEARCH_KEYWORDS,
    CONFIRM_SUBSCRIPTION;

    /** Следующий шаг мастера. Для IDLE и CONFIRM_SUBSCRIPTION следующего нет. */
    public DialogState next() {
        if (this == IDLE || this == CONFIRM_SUBSCRIPTION) {
            throw new IllegalStateException("No next state after " + this);
        }
        return values()[ordinal() + 1];
    }

    /** Один из семи вопросов мастера (на нём работает «Пропустить»). */
    public boolean isQuestion() {
        return this != IDLE && this != CONFIRM_SUBSCRIPTION;
    }
}
