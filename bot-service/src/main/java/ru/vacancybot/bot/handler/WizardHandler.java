package ru.vacancybot.bot.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.vacancybot.bot.fsm.DialogState;
import ru.vacancybot.bot.fsm.InputValidator;
import ru.vacancybot.bot.fsm.Session;
import ru.vacancybot.bot.domain.Coded;
import ru.vacancybot.bot.domain.EmploymentType;
import ru.vacancybot.bot.domain.Experience;
import ru.vacancybot.bot.domain.Region;
import ru.vacancybot.bot.domain.ScheduleType;
import ru.vacancybot.bot.domain.SubscriptionCriteria;
import ru.vacancybot.bot.port.RegionClassifier;
import ru.vacancybot.bot.port.SubscriptionService;
import ru.vacancybot.bot.telegram.TelegramClient;
import ru.vacancybot.bot.view.CallbackData;
import ru.vacancybot.bot.view.Keyboards;
import ru.vacancybot.bot.view.MessageFormatter;

import java.util.Objects;
import java.util.function.Predicate;

/** FSM мастера создания (и редактирования) подписки: «Сценарий №1» из документации. */
@Component
public class WizardHandler {

    private static final Logger log = LoggerFactory.getLogger(WizardHandler.class);

    private final TelegramClient tg;
    private final SubscriptionService subscriptions;
    private final RegionClassifier regions;
    private final MessageFormatter fmt;

    public WizardHandler(TelegramClient tg, SubscriptionService subscriptions,
                         RegionClassifier regions, MessageFormatter fmt) {
        this.tg = tg;
        this.subscriptions = subscriptions;
        this.regions = regions;
        this.fmt = fmt;
    }

    // ---------- вход и выход ----------

    /** Шаг 1. editingSubscriptionId != null — мастер редактирует существующую подписку. */
    public void start(long chatId, Session s, Long editingSubscriptionId) {
        abort(chatId, s);
        s.setEditingSubscriptionId(editingSubscriptionId);
        s.setState(DialogState.SEARCH_TITLE);
        ask(chatId, s, null);
    }

    /** Прерывает мастер: убирает кнопки у последнего вопроса и сбрасывает контекст. */
    public void abort(long chatId, Session s) {
        dropPromptKeyboard(chatId, s);
        s.reset();
    }

    // ---------- текстовый ввод ----------

    public void onText(long chatId, String text, Session s) {
        switch (s.getState()) {
            case IDLE -> tg.sendMessage(chatId, MessageFormatter.UNKNOWN_INPUT, null);
            case SEARCH_TITLE -> {
                var r = InputValidator.title(text);
                if (r.ok()) {
                    s.getDraft().setTitle(r.value());
                    advance(chatId, s);
                } else {
                    ask(chatId, s, r.error());
                }
            }
            case SEARCH_REGION -> {
                var region = regions.findByName(text);
                if (region.isPresent()) {
                    s.getDraft().setRegionCode(region.get().code());
                    advance(chatId, s);
                } else {
                    ask(chatId, s, MessageFormatter.NOTHING_FOUND_REGION);
                }
            }
            case SEARCH_SALARY -> {
                var r = InputValidator.salary(text);
                if (r.ok()) {
                    s.getDraft().setSalaryMin(r.value()); // 0 → null (пропуск)
                    advance(chatId, s);
                } else {
                    ask(chatId, s, r.error());
                }
            }
            case SEARCH_EXPERIENCE, SEARCH_SCHEDULE, SEARCH_EMPLOYMENT ->
                    ask(chatId, s, MessageFormatter.USE_BUTTONS);
            case SEARCH_KEYWORDS -> {
                var r = InputValidator.keywords(text);
                if (r.ok()) {
                    s.getDraft().setKeywords(r.value());
                    advance(chatId, s);
                } else {
                    ask(chatId, s, r.error());
                }
            }
            case CONFIRM_SUBSCRIPTION -> ask(chatId, s, MessageFormatter.CONFIRM_WITH_BUTTONS);
        }
    }

    // ---------- inline-кнопки ----------

    public void onCallback(CallbackCtx cb, Session s) {
        String d = cb.data();
        if (CallbackData.SUB_CONFIRM.equals(d)) {
            confirm(cb, s);
        } else if (CallbackData.SUB_CANCEL.equals(d)) {
            cancel(cb, s);
        } else if (CallbackData.SUB_SKIP.equals(d)) {
            skip(cb, s);
        } else if (d.startsWith(CallbackData.SUB_REG)) {
            choose(cb, s, CallbackData.SUB_REG, DialogState.SEARCH_REGION, code -> {
                if (regions.findByCode(code).isEmpty()) {
                    return false;
                }
                s.getDraft().setRegionCode(code);
                return true;
            });
        } else if (d.startsWith(CallbackData.SUB_SAL)) {
            choose(cb, s, CallbackData.SUB_SAL, DialogState.SEARCH_SALARY, amount -> {
                var r = InputValidator.salary(amount);
                if (!r.ok()) {
                    return false;
                }
                s.getDraft().setSalaryMin(r.value());
                return true;
            });
        } else if (d.startsWith(CallbackData.SUB_EXP)) {
            choose(cb, s, CallbackData.SUB_EXP, DialogState.SEARCH_EXPERIENCE, code -> {
                var e = Coded.byCode(Experience.class, code);
                e.ifPresent(s.getDraft()::setExperience);
                return e.isPresent();
            });
        } else if (d.startsWith(CallbackData.SUB_SCH)) {
            choose(cb, s, CallbackData.SUB_SCH, DialogState.SEARCH_SCHEDULE, code -> {
                var e = Coded.byCode(ScheduleType.class, code);
                e.ifPresent(v -> s.getDraft().setScheduleCode(v.code()));
                return e.isPresent();
            });
        } else if (d.startsWith(CallbackData.SUB_EMP)) {
            choose(cb, s, CallbackData.SUB_EMP, DialogState.SEARCH_EMPLOYMENT, code -> {
                var e = Coded.byCode(EmploymentType.class, code);
                e.ifPresent(v -> s.getDraft().setEmploymentCode(v.code()));
                return e.isPresent();
            });
        } else {
            tg.answerCallbackQuery(cb.queryId(), null);
        }
    }

    private void choose(CallbackCtx cb, Session s, String prefix, DialogState required, Predicate<String> apply) {
        if (!fresh(cb, s, required)) {
            stale(cb);
            return;
        }
        if (!apply.test(CallbackData.arg(cb.data(), prefix))) {
            tg.answerCallbackQuery(cb.queryId(), "Неизвестный вариант");
            return;
        }
        tg.answerCallbackQuery(cb.queryId(), null);
        removeKeyboard(cb.chatId(), cb.messageId());
        s.setPromptMessageId(null);
        advance(cb.chatId(), s);
    }

    private void skip(CallbackCtx cb, Session s) {
        if (!s.getState().isQuestion() || !fresh(cb, s, s.getState())) {
            stale(cb);
            return;
        }
        tg.answerCallbackQuery(cb.queryId(), "Вопрос пропущен");
        removeKeyboard(cb.chatId(), cb.messageId());
        s.setPromptMessageId(null);
        s.getDraft().clear(s.getState());
        advance(cb.chatId(), s);
    }

    private void confirm(CallbackCtx cb, Session s) {
        if (!fresh(cb, s, DialogState.CONFIRM_SUBSCRIPTION)) {
            stale(cb);
            return;
        }
        SubscriptionCriteria criteria = s.getDraft().toCriteria();
        Long editingId = s.getEditingSubscriptionId();
        // Сначала сервис: если он упал, исключение уйдёт в роутер, сессия и кнопки останутся — можно нажать ещё раз.
        if (editingId == null) {
            subscriptions.create(cb.userId(), criteria);
        } else {
            subscriptions.update(cb.userId(), editingId, criteria);
        }
        tg.answerCallbackQuery(cb.queryId(), editingId == null ? "Подписка создана" : "Подписка обновлена");
        removeKeyboard(cb.chatId(), cb.messageId());
        s.reset();
        tg.sendMessage(cb.chatId(),
                editingId == null ? MessageFormatter.SUBSCRIPTION_CREATED : MessageFormatter.SUBSCRIPTION_UPDATED, null);
    }

    private void cancel(CallbackCtx cb, Session s) {
        if (!fresh(cb, s, DialogState.CONFIRM_SUBSCRIPTION)) {
            stale(cb);
            return;
        }
        tg.answerCallbackQuery(cb.queryId(), "Создание подписки отменено");
        removeKeyboard(cb.chatId(), cb.messageId());
        s.reset();
        tg.sendMessage(cb.chatId(), MessageFormatter.SUBSCRIPTION_CANCELLED, null);
    }

    // ---------- переходы и вопросы ----------

    /** Переход к следующему вопросу; после последнего — сводка или возврат к началу, если всё пропущено. */
    private void advance(long chatId, Session s) {
        s.setState(s.getState().next());
        if (s.getState() == DialogState.CONFIRM_SUBSCRIPTION && s.getDraft().isEmpty()) {
            tg.sendMessage(chatId, MessageFormatter.NEED_ONE_PARAM, null);
            s.clearDraft();
            s.setState(DialogState.SEARCH_TITLE);
        }
        ask(chatId, s, null);
    }

    /** Задаёт вопрос текущего состояния (с подсказкой об ошибке, если hint != null). */
    private void ask(long chatId, Session s, String hint) {
        dropPromptKeyboard(chatId, s);
        String body = s.getState() == DialogState.CONFIRM_SUBSCRIPTION
                ? fmt.summary(s.getDraft(), regionName(s.getDraft().getRegionCode()), s.getEditingSubscriptionId() != null)
                : fmt.question(s.getState());
        String text = hint == null ? body : hint + "\n\n" + body;
        int messageId = tg.sendMessage(chatId, text, Keyboards.forState(s.getState(), regions.popular()));
        s.setPromptMessageId(messageId);
    }

    private String regionName(String code) {
        if (code == null) {
            return null;
        }
        return regions.findByCode(code).map(Region::name).orElse(code);
    }

    // ---------- вспомогательное ----------

    /** Кнопка актуальна, если состояние совпадает и нажато последнее сообщение-вопрос. */
    private boolean fresh(CallbackCtx cb, Session s, DialogState required) {
        return s.getState() == required && Objects.equals(s.getPromptMessageId(), cb.messageId());
    }

    private void stale(CallbackCtx cb) {
        tg.answerCallbackQuery(cb.queryId(), "Кнопка устарела. Начните заново: /subscribe");
        removeKeyboard(cb.chatId(), cb.messageId());
    }

    private void dropPromptKeyboard(long chatId, Session s) {
        if (s.getPromptMessageId() != null) {
            removeKeyboard(chatId, s.getPromptMessageId());
            s.setPromptMessageId(null);
        }
    }

    private void removeKeyboard(long chatId, int messageId) {
        try {
            tg.editMessageReplyMarkup(chatId, messageId, null);
        } catch (RuntimeException e) {
            log.debug("Could not remove keyboard from message {}: {}", messageId, e.getMessage());
        }
    }
}
