package ru.vacancybot.bot;

import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.domain.SubscriptionCriteria;
import ru.vacancybot.bot.domain.Subscription;
import ru.vacancybot.bot.stub.StubSubscriptionService;
import ru.vacancybot.bot.view.MessageFormatter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Сообщения мастера подписки не должны висеть в чате: после конца остаётся только итоговое сообщение. */
class WizardCleanupTest {

    private static void assertOnlyLastMessageSurvives(Harness h) {
        var all = h.tg.messages;
        for (int i = 0; i < all.size() - 1; i++) {
            assertTrue(h.tg.deleted.contains(all.get(i).id), "должно быть удалено: " + all.get(i).text);
        }
        assertFalse(h.tg.deleted.contains(all.get(all.size() - 1).id));
        for (int userMessageId : h.userMessageIds) {
            assertTrue(h.tg.deleted.contains(userMessageId), "ответ пользователя должен быть удалён");
        }
    }

    @Test
    void afterConfirm_allWizardMessagesAreDeleted_onlyResultRemains() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("Java");
        h.press("sub_skip");
        h.say("80000");
        for (int i = 0; i < 4; i++) {
            h.press("sub_skip");
        }

        h.press("sub_confirm");

        assertEquals(List.of(MessageFormatter.SUBSCRIPTION_CREATED), h.tg.visibleTexts());
        assertOnlyLastMessageSurvives(h);
        assertEquals(1, h.subs.list(Harness.UID).size());
    }

    @Test
    void afterCancel_wizardMessagesAreDeleted() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("Java");
        for (int i = 0; i < 6; i++) {
            h.press("sub_skip");
        }

        h.press("sub_cancel");

        assertEquals(List.of(MessageFormatter.SUBSCRIPTION_CANCELLED), h.tg.visibleTexts());
        assertOnlyLastMessageSurvives(h);
    }

    @Test
    void invalidAnswersAndHints_areCleanedToo() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("Java");
        h.press("sub_skip");
        h.say("не число");
        h.say("100000");
        for (int i = 0; i < 4; i++) {
            h.press("sub_skip");
        }
        h.press("sub_confirm");

        assertEquals(List.of(MessageFormatter.SUBSCRIPTION_CREATED), h.tg.visibleTexts());
        assertOnlyLastMessageSurvives(h);
    }

    @Test
    void skippingEverything_deletesTheRestartNoticeToo() {
        Harness h = new Harness();
        h.say("/subscribe");
        for (int i = 0; i < 7; i++) {
            h.press("sub_skip");
        }
        h.say("Java");
        for (int i = 0; i < 6; i++) {
            h.press("sub_skip");
        }
        h.press("sub_confirm");

        assertEquals(List.of(MessageFormatter.SUBSCRIPTION_CREATED), h.tg.visibleTexts());
    }

    @Test
    void interruptingWithAnotherCommand_cleansUpTheWizard() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("Java");

        h.say("/help");

        assertEquals(1, h.tg.visibleTexts().size());
        assertTrue(h.lastText().contains("Как это работает"));
    }

    @Test
    void restartingWizard_removesPreviousAttempt() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("Java");
        var firstAttempt = List.copyOf(h.tg.messages);

        h.say("/subscribe");

        for (var m : firstAttempt) {
            assertTrue(h.tg.deleted.contains(m.id));
        }
        assertEquals(List.of("Введите название вакансии (например: Java разработчик):"), h.tg.visibleTexts());
    }

    @Test
    void editFlow_deletesIntroAndQuestions() {
        Harness h = new Harness();
        h.subs.create(Harness.UID, new SubscriptionCriteria("Java", null, null, null, null, null, null));
        h.say("/subscriptions");
        var card = h.tg.last();

        h.press("sub_edit_1");
        h.say("Kotlin");
        for (int i = 0; i < 6; i++) {
            h.press("sub_skip");
        }
        h.press("sub_confirm");

        assertEquals(List.of(card.text, MessageFormatter.SUBSCRIPTION_UPDATED), h.tg.visibleTexts());
    }

    @Test
    void failedConfirm_keepsMessagesSoUserCanRetry() {
        var flaky = new StubSubscriptionService() {
            private boolean failed;

            @Override
            public synchronized Subscription create(long telegramId, SubscriptionCriteria criteria) {
                if (!failed) {
                    failed = true;
                    throw new IllegalStateException("down");
                }
                return super.create(telegramId, criteria);
            }
        };
        Harness h = new Harness(flaky);
        h.say("/subscribe");
        h.say("Java");
        for (int i = 0; i < 6; i++) {
            h.press("sub_skip");
        }

        h.press("sub_confirm");
        assertTrue(h.tg.deleted.isEmpty(), "при ошибке сервиса ничего не удаляем");

        h.press("sub_confirm");
        assertEquals(List.of(MessageFormatter.SUBSCRIPTION_CREATED), h.tg.visibleTexts());
    }

    @Test
    void deleteFailures_doNotBreakTheDialog() {
        Harness h = new Harness();
        h.tg.failDeletes = true;
        h.say("/subscribe");
        h.say("Java");
        for (int i = 0; i < 6; i++) {
            h.press("sub_skip");
        }
        h.press("sub_confirm");

        assertEquals(MessageFormatter.SUBSCRIPTION_CREATED, h.lastText());
        assertEquals(1, h.subs.list(Harness.UID).size());
    }
}
