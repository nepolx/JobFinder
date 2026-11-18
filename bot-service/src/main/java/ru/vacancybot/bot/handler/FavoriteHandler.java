package ru.vacancybot.bot.handler;

import org.springframework.stereotype.Component;
import ru.vacancybot.bot.domain.Coded;
import ru.vacancybot.bot.domain.Favorite;
import ru.vacancybot.bot.domain.FavoriteStatus;
import ru.vacancybot.bot.port.FavoriteService;
import ru.vacancybot.bot.telegram.TelegramClient;
import ru.vacancybot.bot.view.CallbackData;
import ru.vacancybot.bot.view.Keyboards;
import ru.vacancybot.bot.view.MessageFormatter;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** /favorites и кнопки избранного: добавить, убрать, пометка («откликнулся», «жду ответа»). */
@Component
public class FavoriteHandler {

    static final int MAX_CARDS = 10;

    /** vacancyId в common — String (в БД это uuid); допускаем буквы, цифры, «-» и «_», чтобы влезть в 64 байта callback_data. */
    private static final Pattern VACANCY_ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");

    private final TelegramClient tg;
    private final FavoriteService favorites;
    private final MessageFormatter fmt;

    public FavoriteHandler(TelegramClient tg, FavoriteService favorites, MessageFormatter fmt) {
        this.tg = tg;
        this.favorites = favorites;
        this.fmt = fmt;
    }

    public void showList(long chatId, long userId) {
        List<Favorite> list = favorites.list(userId);
        if (list.isEmpty()) {
            tg.sendMessage(chatId, MessageFormatter.NO_FAVORITES, null);
            return;
        }
        for (Favorite f : list.stream().limit(MAX_CARDS).toList()) {
            tg.sendMessage(chatId, fmt.favoriteCard(f), Keyboards.favoriteCard(f));
        }
        if (list.size() > MAX_CARDS) {
            tg.sendMessage(chatId, "Показаны первые " + MAX_CARDS + " из " + list.size() + " вакансий.", null);
        }
    }

    public void onCallback(CallbackCtx cb) {
        String d = cb.data();
        if (d.startsWith(CallbackData.FAV_ADD)) {
            String id = parseVacancyId(cb, CallbackData.arg(d, CallbackData.FAV_ADD));
            if (id != null) {
                add(cb, id);
            }
        } else if (d.startsWith(CallbackData.FAV_DEL)) {
            String id = parseVacancyId(cb, CallbackData.arg(d, CallbackData.FAV_DEL));
            if (id != null) {
                remove(cb, id);
            }
        } else if (d.startsWith(CallbackData.FAV_NOTE)) {
            String id = parseVacancyId(cb, CallbackData.arg(d, CallbackData.FAV_NOTE));
            if (id != null) {
                tg.answerCallbackQuery(cb.queryId(), null);
                tg.editMessageReplyMarkup(cb.chatId(), cb.messageId(), Keyboards.favoriteStatuses(id));
            }
        } else if (d.startsWith(CallbackData.FAV_STATUS)) {
            setStatus(cb, CallbackData.arg(d, CallbackData.FAV_STATUS));
        } else {
            tg.answerCallbackQuery(cb.queryId(), null);
        }
    }

    private void add(CallbackCtx cb, String id) {
        try {
            favorites.add(cb.userId(), id);
        } catch (java.util.NoSuchElementException e) {
            tg.answerCallbackQuery(cb.queryId(), "Эта вакансия уже недоступна");
            return;
        }
        tg.answerCallbackQuery(cb.queryId(), "Добавлено в избранное");
        // Под уведомлением убираем только «В избранное», «Отписаться» остаётся.
        tg.editMessageReplyMarkup(cb.chatId(), cb.messageId(),
                Keyboards.without(cb.markup(), data -> data.equals(CallbackData.FAV_ADD + id)));
    }

    private void remove(CallbackCtx cb, String id) {
        favorites.remove(cb.userId(), id);
        tg.answerCallbackQuery(cb.queryId(), "Удалено из избранного");
        tg.editMessageText(cb.chatId(), cb.messageId(), cb.messageText() + "\n\nУбрана из избранного.", null);
    }

    /** arg = «{statusCode}_{vacancyId}». */
    private void setStatus(CallbackCtx cb, String arg) {
        int sep = arg.indexOf('_');
        Optional<FavoriteStatus> status = sep < 0 ? Optional.empty()
                : Coded.byCode(FavoriteStatus.class, arg.substring(0, sep));
        String id = sep < 0 ? null : parseVacancyId(cb, arg.substring(sep + 1));
        if (id == null && sep >= 0) {
            return; // parseVacancyId уже ответил
        }
        if (status.isEmpty()) {
            tg.answerCallbackQuery(cb.queryId(), "Неизвестный статус");
            return;
        }
        if (favorites.find(cb.userId(), id).isEmpty()) {
            tg.answerCallbackQuery(cb.queryId(), "Вакансии уже нет в избранном");
            tg.editMessageReplyMarkup(cb.chatId(), cb.messageId(), null);
            return;
        }
        favorites.setStatus(cb.userId(), id, status.get());
        tg.answerCallbackQuery(cb.queryId(), "Статус обновлён");
        favorites.find(cb.userId(), id).ifPresent(f ->
                tg.editMessageText(cb.chatId(), cb.messageId(), fmt.favoriteCard(f), Keyboards.favoriteCard(f)));
    }

    private String parseVacancyId(CallbackCtx cb, String raw) {
        if (raw != null && VACANCY_ID.matcher(raw).matches()) {
            return raw;
        }
        tg.answerCallbackQuery(cb.queryId(), "Некорректные данные");
        return null;
    }
}
