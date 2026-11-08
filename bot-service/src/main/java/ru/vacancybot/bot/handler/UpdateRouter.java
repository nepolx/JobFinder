package ru.vacancybot.bot.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.vacancybot.bot.fsm.Session;
import ru.vacancybot.bot.fsm.SessionStore;
import ru.vacancybot.bot.telegram.TelegramClient;
import ru.vacancybot.bot.telegram.dto.CallbackQuery;
import ru.vacancybot.bot.telegram.dto.Message;
import ru.vacancybot.bot.telegram.dto.Update;
import ru.vacancybot.bot.view.CallbackData;
import ru.vacancybot.bot.view.MessageFormatter;

/**
 * Точка входа для всех update от Telegram. Никогда не бросает исключения наружу:
 * для Telegram ответ на webhook всегда 200, иначе он будет повторять доставку.
 */
@Component
public class UpdateRouter {

    private static final Logger log = LoggerFactory.getLogger(UpdateRouter.class);
    private static final int LOCKS = 64;

    private final TelegramClient tg;
    private final SessionStore sessions;
    private final CommandHandler commands;
    private final WizardHandler wizard;
    private final SubscriptionHandler subscriptions;
    private final FavoriteHandler favorites;
    private final Object[] locks = new Object[LOCKS];

    public UpdateRouter(TelegramClient tg, SessionStore sessions, CommandHandler commands, WizardHandler wizard,
                        SubscriptionHandler subscriptions, FavoriteHandler favorites) {
        this.tg = tg;
        this.sessions = sessions;
        this.commands = commands;
        this.wizard = wizard;
        this.subscriptions = subscriptions;
        this.favorites = favorites;
        for (int i = 0; i < LOCKS; i++) {
            locks[i] = new Object();
        }
    }

    public void handle(Update update) {
        if (update == null) {
            return;
        }
        try {
            if (update.callbackQuery() != null) {
                handleCallback(update.updateId(), update.callbackQuery());
            } else if (update.message() != null) {
                handleMessage(update.updateId(), update.message());
            }
        } catch (RuntimeException e) {
            log.error("Unhandled error in update {}", update.updateId(), e);
        }
    }

    private void handleMessage(long updateId, Message m) {
        if (m.text() == null || m.from() == null || m.from().isBot()
                || m.chat() == null || !"private".equals(m.chat().type())) {
            return; // бот работает только в личной переписке и только с текстом
        }
        long chatId = m.chat().id();
        long userId = m.from().id();
        synchronized (lockFor(userId)) {
            Session s = sessions.load(userId);
            try {
                String text = m.text();
                if (text.startsWith("/")) {
                    commands.handle(chatId, userId, CommandHandler.commandOf(text), s);
                } else {
                    wizard.onText(chatId, text, s);
                }
            } catch (RuntimeException e) {
                log.error("Failed to handle message update {}", updateId, e);
                safeSend(chatId);
            } finally {
                sessions.save(userId, s);
            }
        }
    }

    private void handleCallback(long updateId, CallbackQuery q) {
        if (q.id() == null) {
            return;
        }
        Message m = q.message();
        if (q.data() == null || q.from() == null || m == null || m.chat() == null) {
            safeAnswer(q.id(), "Сообщение устарело");
            return;
        }
        CallbackCtx cb = new CallbackCtx(q.id(), m.chat().id(), q.from().id(), m.messageId(),
                m.text(), m.replyMarkup(), q.data());
        synchronized (lockFor(cb.userId())) {
            Session s = sessions.load(cb.userId());
            try {
                dispatch(cb, s);
            } catch (RuntimeException e) {
                log.error("Failed to handle callback update {}", updateId, e);
                safeAnswer(cb.queryId(), MessageFormatter.SERVICE_ERROR);
            } finally {
                sessions.save(cb.userId(), s);
            }
        }
    }

    private void dispatch(CallbackCtx cb, Session s) {
        String d = cb.data();
        if (CallbackData.isWizard(d)) {
            wizard.onCallback(cb, s);
        } else if (d.startsWith("sub_")) {
            subscriptions.onCallback(cb, s);
        } else if (d.startsWith("fav_")) {
            favorites.onCallback(cb);
        } else {
            tg.answerCallbackQuery(cb.queryId(), null);
        }
    }

    private Object lockFor(long userId) {
        return locks[(int) Math.floorMod(userId, (long) LOCKS)];
    }

    private void safeSend(long chatId) {
        try {
            tg.sendMessage(chatId, MessageFormatter.SERVICE_ERROR, null);
        } catch (RuntimeException e) {
            log.warn("Could not send error message: {}", e.getMessage());
        }
    }

    private void safeAnswer(String queryId, String text) {
        try {
            tg.answerCallbackQuery(queryId, text);
        } catch (RuntimeException e) {
            log.warn("Could not answer callback: {}", e.getMessage());
        }
    }
}
