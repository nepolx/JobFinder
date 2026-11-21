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

        assertEquals(List.of(MenuButtons.SUBSCRIBE, MenuButtons.SUBSCRIPTIONS, MenuButtons.BROWSE,
                MenuButtons.FAVORITES, MenuButtons.HELP), labels(h));
        assertTrue(h.tg.last().replyKeyboard.resizeKeyboard());
        assertTrue(h.tg.last().replyKeyboard.persistent());
    }

    @Test
    void help_alsoShowsMenu_forUsersWhoStartedBeforeTheMenuExisted() {
        Harness h = new Harness();
        h.say("/help");
        assertEquals(5, labels(h).size());
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

        h.say(MenuButtons.BROWSE);
        assertTrue(h.lastText().startsWith("🔥 Подборка"));

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
        assertEquals("/browse", MenuButtons.commandFor("  🔥 Подборка "));
        assertNull(MenuButtons.commandFor("Подборка"));
        assertNull(MenuButtons.commandFor(null));
    }
}
