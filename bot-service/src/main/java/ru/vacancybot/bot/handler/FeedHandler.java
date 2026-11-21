package ru.vacancybot.bot.handler;

import org.springframework.stereotype.Component;
import ru.vacancybot.bot.domain.Vacancy;
import ru.vacancybot.bot.port.FavoriteService;
import ru.vacancybot.bot.port.FeedUnavailableException;
import ru.vacancybot.bot.port.VacancyFeedService;
import ru.vacancybot.bot.telegram.TelegramClient;
import ru.vacancybot.bot.view.CallbackData;
import ru.vacancybot.bot.view.Keyboards;
import ru.vacancybot.bot.view.MessageFormatter;

import java.util.NoSuchElementException;
import java.util.Optional;

/** /browse: подборка «как в Tinder». Одна карточка, 👍 кладёт в избранное, 👎 пропускает, карточка заменяется следующей. */
@Component
public class FeedHandler {

    private final TelegramClient tg;
    private final VacancyFeedService feed;
    private final FavoriteService favorites;
    private final MessageFormatter fmt;

    public FeedHandler(TelegramClient tg, VacancyFeedService feed, FavoriteService favorites, MessageFormatter fmt) {
        this.tg = tg;
        this.feed = feed;
        this.favorites = favorites;
        this.fmt = fmt;
    }

    public void start(long chatId, long userId) {
        Optional<Vacancy> next;
        try {
            next = feed.next(userId);
        } catch (FeedUnavailableException e) {
            tg.sendMessage(chatId, MessageFormatter.FEED_UNAVAILABLE, null);
            return;
        }
        if (next.isEmpty()) {
            tg.sendMessage(chatId, MessageFormatter.FEED_EMPTY, null);
        } else {
            tg.sendMessage(chatId, fmt.feedCard(next.get()), Keyboards.feedCard(next.get().id()));
        }
    }

    public void onCallback(CallbackCtx cb) {
        String d = cb.data();
        if (CallbackData.FEED_STOP.equals(d)) {
            tg.answerCallbackQuery(cb.queryId(), "Подборка остановлена");
            tg.editMessageReplyMarkup(cb.chatId(), cb.messageId(), null);
        } else if (d.startsWith(CallbackData.FEED_LIKE)) {
            String id = CallbackData.arg(d, CallbackData.FEED_LIKE);
            if (valid(cb, id)) {
                like(cb, id);
            }
        } else if (d.startsWith(CallbackData.FEED_SKIP)) {
            String id = CallbackData.arg(d, CallbackData.FEED_SKIP);
            if (valid(cb, id)) {
                tg.answerCallbackQuery(cb.queryId(), null);
                markSeen(cb.userId(), id);
                showNext(cb);
            }
        } else {
            tg.answerCallbackQuery(cb.queryId(), null);
        }
    }

    private void like(CallbackCtx cb, String id) {
        String answer = "⭐ В избранном";
        try {
            favorites.add(cb.userId(), id);
        } catch (NoSuchElementException e) {
            answer = "Эта вакансия уже недоступна"; // всё равно идём к следующей
        }
        tg.answerCallbackQuery(cb.queryId(), answer);
        markSeen(cb.userId(), id);
        showNext(cb);
    }

    /** Заменяет текущую карточку следующей; если вакансий больше нет, пишет об этом. */
    private void showNext(CallbackCtx cb) {
        Optional<Vacancy> next;
        try {
            next = feed.next(cb.userId());
        } catch (FeedUnavailableException e) {
            next = Optional.empty();
        }
        if (next.isEmpty()) {
            tg.editMessageText(cb.chatId(), cb.messageId(), MessageFormatter.FEED_EMPTY, null);
        } else {
            tg.editMessageText(cb.chatId(), cb.messageId(), fmt.feedCard(next.get()), Keyboards.feedCard(next.get().id()));
        }
    }

    private void markSeen(long userId, String id) {
        try {
            feed.markSeen(userId, id);
        } catch (FeedUnavailableException ignored) {
            // подборка недоступна: оценка просто не запоминается
        }
    }

    private boolean valid(CallbackCtx cb, String id) {
        if (CallbackData.isVacancyId(id)) {
            return true;
        }
        tg.answerCallbackQuery(cb.queryId(), "Некорректные данные");
        return false;
    }
}
