package ru.vacancybot.bot;

import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.domain.FavoriteStatus;
import ru.vacancybot.bot.domain.Vacancy;
import ru.vacancybot.bot.stub.SampleVacancies;
import ru.vacancybot.bot.view.MessageFormatter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FavoritesFlowTest {

    private static final Vacancy V = SampleVacancies.NEW_VACANCY;
    private static final String ID = V.id();

    @Test
    void notificationHasFavoriteAndUnsubscribeButtons() {
        Harness h = new Harness();
        h.sender.sendNewVacancy(Harness.UID, V, 7);
        assertEquals(List.of("fav_add_" + ID, "sub_del_7"), h.tg.last().callbacks());
    }

    @Test
    void addToFavorites_savesAndKeepsUnsubscribeButton() {
        Harness h = new Harness();
        h.sender.sendNewVacancy(Harness.UID, V, 7);

        h.press("fav_add_" + ID);

        assertEquals("Добавлено в избранное", h.tg.lastAnswer());
        assertEquals(1, h.favs.list(Harness.UID).size());
        assertEquals(FavoriteStatus.SAVED, h.favs.list(Harness.UID).get(0).status());
        assertEquals(List.of("sub_del_7"), h.tg.last().callbacks());
    }

    @Test
    void unsubscribeFromNotification_keepsFavoriteButton() {
        Harness h = new Harness();
        h.sender.sendNewVacancy(Harness.UID, V, 7);
        String text = h.lastText();

        h.press("sub_del_7");

        assertEquals("Подписка удалена", h.tg.lastAnswer());
        assertEquals(List.of("fav_add_" + ID), h.tg.last().callbacks());
        assertEquals(text, h.lastText());
    }

    @Test
    void favoritesList_showsCardsWithStatus() {
        Harness h = new Harness();
        h.say("/favorites");
        assertEquals(MessageFormatter.NO_FAVORITES, h.lastText());

        h.favs.add(Harness.UID, V.id());
        h.say("/favorites");

        assertTrue(h.lastText().startsWith("Junior Java Developer"));
        assertTrue(h.lastText().contains("Статус: Сохранена"));
        assertTrue(h.lastText().endsWith("Ссылка: https://trudvsem.ru/vacancy/card/123456"));
        assertEquals(List.of("fav_note_" + ID, "fav_del_" + ID), h.tg.last().callbacks());
    }

    @Test
    void note_letsUserPickStatusAndUpdatesCard() {
        Harness h = new Harness();
        h.favs.add(Harness.UID, V.id());
        h.say("/favorites");

        h.press("fav_note_" + ID);
        assertEquals(List.of("fav_st_saved_" + ID, "fav_st_applied_" + ID, "fav_st_waiting_" + ID),
                h.tg.last().callbacks());

        h.press("fav_st_applied_" + ID);

        assertEquals("Статус обновлён", h.tg.lastAnswer());
        assertEquals(FavoriteStatus.APPLIED, h.favs.find(Harness.UID, V.id()).orElseThrow().status());
        assertTrue(h.lastText().contains("Статус: Откликнулся"));
        assertEquals(List.of("fav_note_" + ID, "fav_del_" + ID), h.tg.last().callbacks());

        h.press("fav_note_" + ID);
        h.press("fav_st_waiting_" + ID);
        assertTrue(h.lastText().contains("Статус: Жду ответа"));
    }

    @Test
    void remove_deletesFavoriteAndMarksCard() {
        Harness h = new Harness();
        h.favs.add(Harness.UID, V.id());
        h.say("/favorites");

        h.press("fav_del_" + ID);

        assertEquals("Удалено из избранного", h.tg.lastAnswer());
        assertTrue(h.favs.list(Harness.UID).isEmpty());
        assertTrue(h.lastText().endsWith("Убрана из избранного."));
        assertTrue(h.tg.last().callbacks().isEmpty());
    }

    @Test
    void malformedCallbackData_isAnsweredAndIgnored() {
        Harness h = new Harness();
        h.sender.sendNewVacancy(Harness.UID, V, 7);
        var msg = h.tg.last();

        h.pressOn(msg, "fav_add_!!bad!!");
        assertEquals("Некорректные данные", h.tg.lastAnswer());

        h.pressOn(msg, "sub_del_abc");
        assertEquals("Некорректные данные", h.tg.lastAnswer());

        h.pressOn(msg, "fav_st_unknown_" + ID);
        assertEquals("Неизвестный статус", h.tg.lastAnswer());

        h.pressOn(msg, "garbage");
        assertEquals(null, h.tg.lastAnswer());
        assertTrue(h.favs.list(Harness.UID).isEmpty());
    }

    @Test
    void statusOfRemovedFavorite_isRejected() {
        Harness h = new Harness();
        h.favs.add(Harness.UID, V.id());
        h.say("/favorites");
        var card = h.tg.last();
        h.favs.remove(Harness.UID, V.id());

        h.pressOn(card, "fav_st_applied_" + ID);

        assertEquals("Вакансии уже нет в избранном", h.tg.lastAnswer());
        assertTrue(card.callbacks().isEmpty());
    }
}
