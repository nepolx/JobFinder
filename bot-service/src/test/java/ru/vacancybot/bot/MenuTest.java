package ru.vacancybot.bot;

import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.telegram.dto.KeyboardButton;
import ru.vacancybot.bot.view.MenuButtons;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Постоянное меню под полем ввода («горячие клавиши»). */
class MenuTest {

    private static List<String> labels(Harness h) {
        return h.tg.last().replyKeyboard.keyboard().stream()
                .flatMap(List::stream).map(KeyboardButton::text).toList();
    }

    @Test
    void start_attachesPersistentMenuWithAllCommands() {
        Harness h = new Harness();
        h.say("/start");

        assertEquals(List.of(MenuButtons.SUBSCRIBE, MenuButtons.SUBSCRIPTIONS,
                MenuButtons.FAVORITES, MenuButtons.HELP), labels(h));
        assertTrue(h.tg.last().replyKeyboard.resizeKeyboard());
        assertTrue(h.tg.last().replyKeyboard.persistent());
    }

    @Test
    void help_alsoShowsMenu_forUsersWhoStartedBeforeTheMenuExisted() {
        Harness h = new Harness();
        h.say("/help");
        assertEquals(4, labels(h).size());
    }

    @Test
    void menuButtons_actLikeCommands() {
        Harness h = new Harness();

        h.say(MenuButtons.SUBSCRIPTIONS);
        assertEquals("У вас пока нет подписок. Создайте первую: /subscribe", h.lastText());

        h.say(MenuButtons.FAVORITES);
        assertTrue(h.lastText().startsWith("В избранном пусто"));

        h.say(MenuButtons.HELP);
        assertTrue(h.lastText().contains("Как это работает"));

        h.say(MenuButtons.SUBSCRIBE);
        assertEquals("Введите название вакансии (например: Java разработчик):", h.lastText());
    }

    @Test
    void menuButtonInsideWizard_interruptsItInsteadOfBeingTakenAsAnAnswer() {
        Harness h = new Harness();
        h.say(MenuButtons.SUBSCRIBE);
        h.say("Java");

        h.say(MenuButtons.SUBSCRIPTIONS);

        assertEquals("У вас пока нет подписок. Создайте первую: /subscribe", h.lastText());
        h.say("Python");
        assertEquals("Я вас не понял. Список команд: /help", h.lastText());
    }

    @Test
    void commandFor_matchesOnlyExactMenuLabels() {
        assertEquals("/favorites", MenuButtons.commandFor("  ⭐ Избранное "));
        assertNull(MenuButtons.commandFor("Избранное"));
        assertNull(MenuButtons.commandFor(null));
    }

    @Test
    void tappingAButtonFromTheOldMenu_refreshesTheKeyboardInsteadOfSayingIDontUnderstand() {
        Harness h = new Harness();

        h.say("🔥 Подборка");

        assertEquals(ru.vacancybot.bot.view.MessageFormatter.MENU_UPDATED, h.lastText());
        assertEquals(4, labels(h).size());
        assertTrue(!labels(h).contains("🔥 Подборка"));
    }

    @Test
    void oldMenuButtonInsideWizard_alsoInterruptsIt() {
        Harness h = new Harness();
        h.say(MenuButtons.SUBSCRIBE);
        h.say("Java");

        h.say("🔥 Подборка");

        assertEquals(ru.vacancybot.bot.view.MessageFormatter.MENU_UPDATED, h.tg.visibleTexts().get(h.tg.visibleTexts().size() - 1));
        assertEquals(1, h.tg.visibleTexts().size());
    }
}
