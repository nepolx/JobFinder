package ru.vacancybot.bot;

import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.domain.FavoriteStatus;
import ru.vacancybot.bot.port.FeedUnavailableException;
import ru.vacancybot.bot.port.VacancyFeedService;
import ru.vacancybot.bot.stub.SampleVacancies;
import ru.vacancybot.bot.stub.StubFavoriteService;
import ru.vacancybot.bot.stub.StubSubscriptionService;
import ru.vacancybot.bot.view.MessageFormatter;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Подборка «как в Tinder»: 👍 в избранное, 👎 пропустить, карточка заменяется следующей. */
class FeedFlowTest {

    private static final String FIRST = SampleVacancies.FEED.get(0).id();
    private static final String SECOND = SampleVacancies.FEED.get(1).id();

    @Test
    void browse_showsFirstCardWithSwipeButtons() {
        Harness h = new Harness();
        h.say("/browse");

        assertTrue(h.lastText().startsWith("🔥 Подборка\nJunior Java Developer"));
        assertTrue(h.lastText().endsWith("Ссылка: https://trudvsem.ru/vacancy/card/123456"));
        assertEquals(List.of("feed_skip_" + FIRST, "feed_like_" + FIRST, "feed_stop"), h.tg.last().callbacks());
    }

    @Test
    void like_savesFavorite_andReplacesCardWithNextOne() {
        Harness h = new Harness();
        h.say("/browse");
        var card = h.tg.last();
        int before = h.tg.messages.size();

        h.press("feed_like_" + FIRST);

        assertEquals("⭐ В избранном", h.tg.lastAnswer());
        assertEquals(FavoriteStatus.SAVED, h.favs.find(Harness.UID, FIRST).orElseThrow().status());
        assertEquals(before, h.tg.messages.size(), "новое сообщение не создаётся: карточка заменяется");
        assertTrue(card.text.contains("Java Developer"));
        assertEquals(List.of("feed_skip_" + SECOND, "feed_like_" + SECOND, "feed_stop"), card.callbacks());
    }

    @Test
    void skip_doesNotSaveFavorite_andMovesOn() {
        Harness h = new Harness();
        h.say("/browse");
        var card = h.tg.last();

        h.press("feed_skip_" + FIRST);

        assertTrue(h.favs.list(Harness.UID).isEmpty());
        assertEquals(List.of("feed_skip_" + SECOND, "feed_like_" + SECOND, "feed_stop"), card.callbacks());
    }

    @Test
    void ratedVacanciesAreNotShownAgain_andFeedEndsGracefully() {
        Harness h = new Harness();
        h.say("/browse");
        var card = h.tg.last();

        for (var v : SampleVacancies.FEED) {
            h.pressOn(card, "feed_skip_" + v.id());
        }

        assertEquals(MessageFormatter.FEED_EMPTY, card.text);
        assertTrue(card.callbacks().isEmpty());

        h.say("/browse");
        assertEquals(MessageFormatter.FEED_EMPTY, h.lastText());
    }

    @Test
    void doubleTapOnTheSameCard_doesNotSkipTheNextOne() {
        Harness h = new Harness();
        h.say("/browse");
        var card = h.tg.last();

        h.pressOn(card, "feed_skip_" + FIRST);
        h.pressOn(card, "feed_skip_" + FIRST);

        assertEquals(List.of("feed_skip_" + SECOND, "feed_like_" + SECOND, "feed_stop"), card.callbacks());
    }

    @Test
    void stop_removesButtonsButKeepsTheCard() {
        Harness h = new Harness();
        h.say("/browse");
        var card = h.tg.last();
        String text = card.text;

        h.press("feed_stop");

        assertEquals("Подборка остановлена", h.tg.lastAnswer());
        assertTrue(card.callbacks().isEmpty());
        assertEquals(text, card.text);
    }

    @Test
    void likeOfVacancyThatDisappeared_isReportedButFeedContinues() {
        var gone = new StubFavoriteService() {
            @Override
            public synchronized void add(long telegramId, String vacancyId) {
                throw new NoSuchElementException("gone");
            }
        };
        Harness h = new Harness(new StubSubscriptionService(), gone);
        h.say("/browse");
        var card = h.tg.last();

        h.press("feed_like_" + FIRST);

        assertEquals("Эта вакансия уже недоступна", h.tg.lastAnswer());
        assertEquals(List.of("feed_skip_" + SECOND, "feed_like_" + SECOND, "feed_stop"), card.callbacks());
    }

    @Test
    void unavailableFeed_isExplainedAndDoesNotBreakTheBot() {
        VacancyFeedService broken = new VacancyFeedService() {
            @Override
            public Optional<ru.vacancybot.bot.domain.Vacancy> next(long telegramId) {
                throw new FeedUnavailableException("no endpoint");
            }

            @Override
            public void markSeen(long telegramId, String vacancyId) {
                throw new FeedUnavailableException("no endpoint");
            }
        };
        Harness h = new Harness(new StubSubscriptionService(), new StubFavoriteService(), broken);

        h.say("/browse");
        assertEquals(MessageFormatter.FEED_UNAVAILABLE, h.lastText());
    }

    @Test
    void malformedFeedCallback_isRejected() {
        Harness h = new Harness();
        h.say("/browse");
        h.pressOn(h.tg.last(), "feed_like_!!bad!!");
        assertEquals("Некорректные данные", h.tg.lastAnswer());
        assertTrue(h.favs.list(Harness.UID).isEmpty());
    }
}
