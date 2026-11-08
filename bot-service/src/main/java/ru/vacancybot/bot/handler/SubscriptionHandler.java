package ru.vacancybot.bot.handler;

import org.springframework.stereotype.Component;
import ru.vacancybot.bot.fsm.Session;
import ru.vacancybot.bot.domain.Region;
import ru.vacancybot.bot.domain.Subscription;
import ru.vacancybot.bot.port.RegionClassifier;
import ru.vacancybot.bot.port.SubscriptionService;
import ru.vacancybot.bot.telegram.TelegramClient;
import ru.vacancybot.bot.telegram.dto.InlineKeyboardMarkup;
import ru.vacancybot.bot.view.CallbackData;
import ru.vacancybot.bot.view.Keyboards;
import ru.vacancybot.bot.view.MessageFormatter;

import java.util.List;

/** /subscriptions: список подписок, изменение, пауза/возобновление, удаление. */
@Component
public class SubscriptionHandler {

    /** Telegram ограничивает частоту сообщений в один чат, поэтому показываем не больше 10 карточек. */
    static final int MAX_CARDS = 10;

    private final TelegramClient tg;
    private final SubscriptionService subscriptions;
    private final RegionClassifier regions;
    private final MessageFormatter fmt;
    private final WizardHandler wizard;

    public SubscriptionHandler(TelegramClient tg, SubscriptionService subscriptions, RegionClassifier regions,
                               MessageFormatter fmt, WizardHandler wizard) {
        this.tg = tg;
        this.subscriptions = subscriptions;
        this.regions = regions;
        this.fmt = fmt;
        this.wizard = wizard;
    }

    public void showList(long chatId, long userId) {
        List<Subscription> list = subscriptions.list(userId);
        if (list.isEmpty()) {
            tg.sendMessage(chatId, MessageFormatter.NO_SUBSCRIPTIONS, null);
            return;
        }
        for (Subscription s : list.stream().limit(MAX_CARDS).toList()) {
            tg.sendMessage(chatId, card(s), Keyboards.subscriptionCard(s));
        }
        if (list.size() > MAX_CARDS) {
            tg.sendMessage(chatId, "Показаны первые " + MAX_CARDS + " из " + list.size() + " подписок.", null);
        }
    }

    public void onCallback(CallbackCtx cb, Session session) {
        String d = cb.data();
        if (d.startsWith(CallbackData.SUB_DEL)) {
            Long id = parseId(cb, CallbackData.SUB_DEL);
            if (id != null) {
                delete(cb, id);
            }
        } else if (d.startsWith(CallbackData.SUB_PAUSE)) {
            Long id = parseId(cb, CallbackData.SUB_PAUSE);
            if (id != null) {
                toggle(cb, id, false);
            }
        } else if (d.startsWith(CallbackData.SUB_RESUME)) {
            Long id = parseId(cb, CallbackData.SUB_RESUME);
            if (id != null) {
                toggle(cb, id, true);
            }
        } else if (d.startsWith(CallbackData.SUB_EDIT)) {
            Long id = parseId(cb, CallbackData.SUB_EDIT);
            if (id != null) {
                edit(cb, id, session);
            }
        } else {
            tg.answerCallbackQuery(cb.queryId(), null);
        }
    }

    private void delete(CallbackCtx cb, long id) {
        subscriptions.delete(cb.userId(), id);
        tg.answerCallbackQuery(cb.queryId(), "Подписка удалена");
        // Убираем только кнопки этой подписки: под уведомлением о вакансии «В избранное» остаётся.
        InlineKeyboardMarkup rest = Keyboards.without(cb.markup(),
                data -> data.startsWith("sub_") && data.endsWith("_" + id));
        if (rest.isEmpty()) {
            tg.editMessageText(cb.chatId(), cb.messageId(), cb.messageText() + "\n\nПодписка удалена.", null);
        } else {
            tg.editMessageReplyMarkup(cb.chatId(), cb.messageId(), rest);
        }
    }

    private void toggle(CallbackCtx cb, long id, boolean active) {
        var found = subscriptions.find(cb.userId(), id);
        if (found.isEmpty()) {
            notFound(cb);
            return;
        }
        subscriptions.setActive(cb.userId(), id, active);
        tg.answerCallbackQuery(cb.queryId(), active ? "Подписка возобновлена" : "Подписка на паузе");
        Subscription updated = new Subscription(id, found.get().criteria(), active);
        tg.editMessageText(cb.chatId(), cb.messageId(), card(updated), Keyboards.subscriptionCard(updated));
    }

    private void edit(CallbackCtx cb, long id, Session session) {
        if (subscriptions.find(cb.userId(), id).isEmpty()) {
            notFound(cb);
            return;
        }
        tg.answerCallbackQuery(cb.queryId(), null);
        tg.sendMessage(cb.chatId(), fmt.editingIntro(id), null);
        wizard.start(cb.chatId(), session, id);
    }

    private void notFound(CallbackCtx cb) {
        tg.answerCallbackQuery(cb.queryId(), "Подписка не найдена");
        tg.editMessageReplyMarkup(cb.chatId(), cb.messageId(), null);
    }

    private String card(Subscription s) {
        String code = s.criteria().regionCode();
        String regionName = code == null ? null : regions.findByCode(code).map(Region::name).orElse(code);
        return fmt.subscriptionCard(s, regionName);
    }

    private Long parseId(CallbackCtx cb, String prefix) {
        try {
            return Long.parseLong(CallbackData.arg(cb.data(), prefix));
        } catch (NumberFormatException e) {
            tg.answerCallbackQuery(cb.queryId(), "Некорректные данные");
            return null;
        }
    }
}
