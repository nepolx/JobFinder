package ru.vacancybot.bot.handler;

import org.springframework.stereotype.Component;
import ru.vacancybot.bot.fsm.Session;
import ru.vacancybot.bot.port.UserService;
import ru.vacancybot.bot.telegram.TelegramClient;
import ru.vacancybot.bot.view.MessageFormatter;

import java.util.Locale;

/** Системные команды: /start, /subscribe, /subscriptions, /favorites, /help. */
@Component
public class CommandHandler {

    private final TelegramClient tg;
    private final UserService users;
    private final MessageFormatter fmt;
    private final WizardHandler wizard;
    private final SubscriptionHandler subscriptions;
    private final FavoriteHandler favorites;

    public CommandHandler(TelegramClient tg, UserService users, MessageFormatter fmt, WizardHandler wizard,
                          SubscriptionHandler subscriptions, FavoriteHandler favorites) {
        this.tg = tg;
        this.users = users;
        this.fmt = fmt;
        this.wizard = wizard;
        this.subscriptions = subscriptions;
        this.favorites = favorites;
    }

    /** «/Start@MyBot payload» → «/start». */
    public static String commandOf(String text) {
        String first = text.trim().split("\\s+", 2)[0];
        int at = first.indexOf('@');
        if (at > 0) {
            first = first.substring(0, at);
        }
        return first.toLowerCase(Locale.ROOT);
    }

    public void handle(long chatId, long userId, String command, Session s) {
        wizard.abort(chatId, s); // любая команда прерывает незавершённый мастер
        switch (command) {
            case "/start" -> {
                users.register(userId);
                tg.sendMessage(chatId, fmt.welcome(), null);
            }
            case "/help" -> tg.sendMessage(chatId, fmt.help(), null);
            case "/subscribe" -> wizard.start(chatId, s, null);
            case "/subscriptions" -> subscriptions.showList(chatId, userId);
            case "/favorites" -> favorites.showList(chatId, userId);
            default -> tg.sendMessage(chatId, MessageFormatter.UNKNOWN_COMMAND, null);
        }
    }
}
