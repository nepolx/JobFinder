package ru.vacancybot.bot.fsm;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Контекст диалога одного пользователя. Сериализуемый, чтобы его можно было вынести в Redis. */
public class Session implements Serializable {

    private static final long serialVersionUID = 1L;

    private DialogState state = DialogState.IDLE;
    private SubscriptionDraft draft = new SubscriptionDraft();
    private Long editingSubscriptionId;
    private Integer promptMessageId;
    private List<Integer> wizardMessageIds = new ArrayList<>();

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

    /** message_id сообщений мастера (вопросы бота и ответы пользователя): по окончании мастера они удаляются. */
    public List<Integer> getWizardMessageIds() {
        return wizardMessageIds;
    }

    public void setWizardMessageIds(List<Integer> wizardMessageIds) {
        this.wizardMessageIds = wizardMessageIds == null ? new ArrayList<>() : new ArrayList<>(wizardMessageIds);
    }

    /** Запоминает сообщение для последующей очистки (без дублей, не больше 60 штук). */
    public void trackMessage(int messageId) {
        if (wizardMessageIds.size() < 60 && !wizardMessageIds.contains(messageId)) {
            wizardMessageIds.add(messageId);
        }
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
        wizardMessageIds = new ArrayList<>();
    }
}
