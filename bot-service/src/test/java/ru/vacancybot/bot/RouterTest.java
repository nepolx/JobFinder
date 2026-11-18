package ru.vacancybot.bot;

import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.handler.CommandHandler;
import ru.vacancybot.bot.telegram.dto.CallbackQuery;
import ru.vacancybot.bot.telegram.dto.Chat;
import ru.vacancybot.bot.telegram.dto.Message;
import ru.vacancybot.bot.telegram.dto.Update;
import ru.vacancybot.bot.telegram.dto.User;
import ru.vacancybot.bot.view.MessageFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterTest {

    @Test
    void commandOf_normalizesCommands() {
        assertEquals("/start", CommandHandler.commandOf("/start"));
        assertEquals("/start", CommandHandler.commandOf("/Start@JobFinderBot payload"));
        assertEquals("/help", CommandHandler.commandOf("  /help  "));
        assertEquals("/subscribe", CommandHandler.commandOf("/subscribe\nпосле переноса"));
    }

    @Test
    void start_registersUserAndGreets() {
        Harness h = new Harness();
        h.say("/start");
        assertEquals(h.fmt.welcome(), h.lastText());
        assertTrue(h.users.isRegistered(Harness.UID));
    }

    @Test
    void unknownCommand_andPlainTextOutsideWizard() {
        Harness h = new Harness();
        h.say("/dance");
        assertEquals(MessageFormatter.UNKNOWN_COMMAND, h.lastText());
        h.say("привет");
        assertEquals(MessageFormatter.UNKNOWN_INPUT, h.lastText());
    }

    @Test
    void ignoresGroupChatsBotsAndNonTextUpdates() {
        Harness h = new Harness();
        h.router.handle(new Update(1, new Message(1, new User(Harness.UID, false), new Chat(-100, "group"), "/start", null), null));
        h.router.handle(new Update(2, new Message(1, new User(7, true), new Chat(7, "private"), "/start", null), null));
        h.router.handle(new Update(3, new Message(1, new User(Harness.UID, false), new Chat(Harness.UID, "private"), null, null), null));
        h.router.handle(new Update(4, null, null));
        h.router.handle(null);
        assertTrue(h.tg.messages.isEmpty());
    }

    @Test
    void callbackWithoutMessage_isAnsweredSoSpinnerStops() {
        Harness h = new Harness();
        h.router.handle(new Update(1, null, new CallbackQuery("q1", new User(Harness.UID, false), null, "sub_skip")));
        assertEquals("Сообщение устарело", h.tg.lastAnswer());
    }

    @Test
    void unknownCallback_isAnsweredWithoutText() {
        Harness h = new Harness();
        h.say("/help");
        h.pressOn(h.tg.last(), "whatever");
        assertEquals(1, h.tg.answers.size());
        assertNull(h.tg.lastAnswer());
    }

    @Test
    void failureInsideHandler_neverEscapesRouter_andUserGetsMessage() {
        var broken = new ru.vacancybot.bot.stub.StubSubscriptionService() {
            @Override
            public synchronized java.util.List<ru.vacancybot.bot.domain.Subscription> list(long telegramId) {
                throw new IllegalStateException("db is down");
            }
        };
        Harness h = new Harness(broken);
        h.say("/subscriptions");
        assertEquals(MessageFormatter.SERVICE_ERROR, h.lastText());
    }

    @Test
    void sessionsOfDifferentUsersAreIndependent() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.router.handle(new Update(99, new Message(1, new User(555, false), new Chat(555, "private"), "привет", null), null));
        assertEquals(MessageFormatter.UNKNOWN_INPUT, h.lastText());
        h.say("Java");
        assertTrue(h.lastText().startsWith("Введите регион поиска"));
    }
}
