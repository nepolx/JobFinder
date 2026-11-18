package ru.vacancybot.bot;

import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.domain.SubscriptionCriteria;
import ru.vacancybot.bot.stub.StubRegionClassifier;
import ru.vacancybot.bot.view.MessageFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionManagementTest {

    private static final SubscriptionCriteria JAVA =
            new SubscriptionCriteria("Java", StubRegionClassifier.MOSCOW.code(), 50_000, "1_3", "full", null, null);

    @Test
    void emptyList_suggestsToCreateFirstSubscription() {
        Harness h = new Harness();
        h.say("/subscriptions");
        assertEquals(MessageFormatter.NO_SUBSCRIPTIONS, h.lastText());
    }

    @Test
    void list_showsCardPerSubscriptionWithEditPauseDeleteButtons() {
        Harness h = new Harness();
        h.subs.create(Harness.UID, JAVA);
        h.subs.create(Harness.UID, new SubscriptionCriteria("Go", null, null, null, null, null, "kafka"));

        h.say("/subscriptions");

        assertEquals(2, h.tg.messages.size());
        var first = h.tg.messages.get(0);
        assertTrue(first.text.startsWith("Подписка №1 (активна)"));
        assertTrue(first.text.contains("• Регион: Москва"));
        assertTrue(first.text.contains("• Опыт: 1–3 года"));
        assertEquals(java.util.List.of("sub_edit_1", "sub_pause_1", "sub_del_1"), first.callbacks());
        assertTrue(h.tg.messages.get(1).text.contains("• Регион: не важно"));
    }

    @Test
    void pauseAndResume_updateCardAndService() {
        Harness h = new Harness();
        h.subs.create(Harness.UID, JAVA);
        h.say("/subscriptions");

        h.press("sub_pause_1");
        assertEquals("Подписка на паузе", h.tg.lastAnswer());
        assertTrue(h.lastText().startsWith("Подписка №1 (на паузе)"));
        assertTrue(h.tg.last().callbacks().contains("sub_resume_1"));
        assertFalse(h.subs.list(Harness.UID).get(0).active());

        h.press("sub_resume_1");
        assertEquals("Подписка возобновлена", h.tg.lastAnswer());
        assertTrue(h.lastText().startsWith("Подписка №1 (активна)"));
        assertTrue(h.subs.list(Harness.UID).get(0).active());
    }

    @Test
    void delete_removesSubscriptionAndMarksCard() {
        Harness h = new Harness();
        h.subs.create(Harness.UID, JAVA);
        h.say("/subscriptions");

        h.press("sub_del_1");

        assertEquals("Подписка удалена", h.tg.lastAnswer());
        assertTrue(h.subs.list(Harness.UID).isEmpty());
        assertTrue(h.lastText().endsWith("Подписка удалена."));
        assertTrue(h.tg.last().callbacks().isEmpty());
    }

    @Test
    void pausingMissingSubscription_answersNotFound() {
        Harness h = new Harness();
        h.subs.create(Harness.UID, JAVA);
        h.say("/subscriptions");
        var card = h.tg.last();
        h.subs.delete(Harness.UID, 1);

        h.pressOn(card, "sub_pause_1");

        assertEquals("Подписка не найдена", h.tg.lastAnswer());
        assertTrue(card.callbacks().isEmpty());
    }

    @Test
    void edit_runsWizardAndUpdatesSameSubscription() {
        Harness h = new Harness();
        h.subs.create(Harness.UID, JAVA);
        h.say("/subscriptions");

        h.press("sub_edit_1");
        assertTrue(h.texts().contains(h.fmt.editingIntro(1)));
        assertEquals("Введите название вакансии (например: Java разработчик):", h.lastText());

        h.say("Kotlin");
        h.press("sub_skip");          // регион
        h.press("sub_sal_100000");
        for (int i = 0; i < 4; i++) { // опыт, график, занятость, слова
            h.press("sub_skip");
        }
        assertTrue(h.lastText().endsWith("Сохранить изменения с этими параметрами?"));

        h.press("sub_confirm");

        assertEquals("Подписка обновлена", h.tg.lastAnswer());
        assertEquals(MessageFormatter.SUBSCRIPTION_UPDATED, h.lastText());
        var subs = h.subs.list(Harness.UID);
        assertEquals(1, subs.size());
        assertEquals(1, subs.get(0).id());
        assertEquals(new SubscriptionCriteria("Kotlin", null, 100_000, null, null, null, null),
                subs.get(0).criteria());
    }

    @Test
    void list_isCappedToTenCards() {
        Harness h = new Harness();
        for (int i = 0; i < 12; i++) {
            h.subs.create(Harness.UID, JAVA);
        }
        h.say("/subscriptions");
        assertEquals(11, h.tg.messages.size());
        assertEquals("Показаны первые 10 из 12 подписок.", h.lastText());
    }
}
