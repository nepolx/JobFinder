package ru.vacancybot.bot.fsm;

import java.io.Serializable;

/** Контекст диалога одного пользователя. Сериализуемый, чтобы его можно было вынести в Redis. */
public class Session implements Serializable {

    private static final long serialVersionUID = 1L;

    private DialogState state = DialogState.IDLE;
    private SubscriptionDraft draft = new SubscriptionDraft();
    private Long editingSubscriptionId;
    private Integer promptMessageId;

    public DialogState getState() {
        return state;
    }

    public void setState(DialogState state) {
        this.state = state;
    }

    public SubscriptionDraft getDraft() {
        return draft;
    }

    public void setDraft(SubscriptionDraft draft) {
        this.draft = draft;
    }

    public Long getEditingSubscriptionId() {
        return editingSubscriptionId;
    }

    public void setEditingSubscriptionId(Long editingSubscriptionId) {
        this.editingSubscriptionId = editingSubscriptionId;
    }

    /** message_id последнего вопроса мастера: у него убираем кнопки после ответа. */
    public Integer getPromptMessageId() {
        return promptMessageId;
    }

    public void setPromptMessageId(Integer promptMessageId) {
        this.promptMessageId = promptMessageId;
    }

    /** Новый чистый черновик, состояние не трогает. */
    public void clearDraft() {
        draft = new SubscriptionDraft();
    }

    /** Полный сброс: состояние IDLE, пустой контекст. */
    public void reset() {
        state = DialogState.IDLE;
        draft = new SubscriptionDraft();
        editingSubscriptionId = null;
        promptMessageId = null;
    }
}
