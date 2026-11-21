package ru.vacancybot.bot;

import ru.vacancybot.bot.fsm.InMemorySessionStore;
import ru.vacancybot.bot.handler.CommandHandler;
import ru.vacancybot.bot.handler.FavoriteHandler;
import ru.vacancybot.bot.handler.FeedHandler;
import ru.vacancybot.bot.handler.SubscriptionHandler;
import ru.vacancybot.bot.handler.UpdateRouter;
import ru.vacancybot.bot.handler.WizardHandler;
import ru.vacancybot.bot.notify.NotificationSender;
import ru.vacancybot.bot.stub.StubFavoriteService;
import ru.vacancybot.bot.stub.StubRegionClassifier;
import ru.vacancybot.bot.stub.StubSubscriptionService;
import ru.vacancybot.bot.stub.StubUserService;
import ru.vacancybot.bot.stub.StubVacancyFeedService;
import ru.vacancybot.bot.port.VacancyFeedService;
import ru.vacancybot.bot.telegram.dto.CallbackQuery;
import ru.vacancybot.bot.telegram.dto.Chat;
import ru.vacancybot.bot.telegram.dto.Message;
import ru.vacancybot.bot.telegram.dto.Update;
import ru.vacancybot.bot.telegram.dto.User;
import ru.vacancybot.bot.view.MessageFormatter;

import java.util.List;

/** Собирает весь бот без Spring: роутер + заглушки + фейк Telegram. Имитирует действия пользователя. */
public class Harness {

    public static final long UID = 4242;

    public final FakeTelegramClient tg = new FakeTelegramClient();
    public final StubSubscriptionService subs;
    public final StubFavoriteService favs;
    public final StubUserService users = new StubUserService();
    public final StubRegionClassifier regions = new StubRegionClassifier();
    public final MessageFormatter fmt = new MessageFormatter("@jobfinder_support");
    public final NotificationSender sender = new NotificationSender(tg, fmt);
    public final UpdateRouter router;
    public final VacancyFeedService feed;
    public final java.util.List<Integer> userMessageIds = new java.util.ArrayList<>();
    private long seq = 0;

    public Harness() {
        this(new StubSubscriptionService());
    }

    public Harness(StubSubscriptionService subs) {
        this(subs, new StubFavoriteService());
    }

    public Harness(StubSubscriptionService subs, StubFavoriteService favs) {
        this(subs, favs, new StubVacancyFeedService());
    }

    public Harness(StubSubscriptionService subs, StubFavoriteService favs, VacancyFeedService feed) {
        this.subs = subs;
        this.favs = favs;
        this.feed = feed;
        WizardHandler wizard = new WizardHandler(tg, subs, regions, fmt);
        SubscriptionHandler subscriptionHandler = new SubscriptionHandler(tg, subs, regions, fmt, wizard);
        FavoriteHandler favoriteHandler = new FavoriteHandler(tg, favs, fmt);
        FeedHandler feedHandler = new FeedHandler(tg, feed, favs, fmt);
        CommandHandler commands = new CommandHandler(tg, users, fmt, wizard, subscriptionHandler, favoriteHandler, feedHandler);
        this.router = new UpdateRouter(tg, new InMemorySessionStore(), commands, wizard, subscriptionHandler, favoriteHandler, feedHandler);
    }

    /** Пользователь пишет сообщение в личку. */
    public void say(String text) {
        int messageId = (int) (10_000 + (++seq));
        userMessageIds.add(messageId);
        router.handle(new Update(seq,
                new Message(messageId, new User(UID, false), new Chat(UID, "private"), text, null), null));
    }

    /** Пользователь нажимает кнопку на самом свежем сообщении, где она есть. */
    public void press(String data) {
        for (int i = tg.messages.size() - 1; i >= 0; i--) {
            FakeTelegramClient.Msg m = tg.messages.get(i);
            if (m.callbacks().contains(data)) {
                pressOn(m, data);
                return;
            }
        }
        throw new AssertionError("No button " + data + " found. Last message: " + lastText());
    }

    /** Нажатие кнопки на конкретном сообщении (в том числе старом). */
    public void pressOn(FakeTelegramClient.Msg m, String data) {
        router.handle(new Update(++seq, null, new CallbackQuery("cq" + seq, new User(UID, false),
                new Message(m.id, new User(1, true), new Chat(UID, "private"), m.text, m.keyboard), data)));
    }

    public String lastText() {
        return tg.last().text;
    }

    public List<String> texts() {
        return tg.messages.stream().map(m -> m.text).toList();
    }
}
