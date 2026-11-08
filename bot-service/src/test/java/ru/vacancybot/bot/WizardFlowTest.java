package ru.vacancybot.bot;

import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.domain.SubscriptionCriteria;
import ru.vacancybot.bot.stub.StubRegionClassifier;
import ru.vacancybot.bot.stub.StubSubscriptionService;
import ru.vacancybot.bot.view.MessageFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WizardFlowTest {

    private static final String SPB = StubRegionClassifier.SPB.code();
    private static final String MOSCOW = StubRegionClassifier.MOSCOW.code();

    @Test
    void happyPath_createsSubscriptionWithAllParameters() {
        Harness h = new Harness();

        h.say("/subscribe");
        assertEquals("Введите название вакансии (например: Java разработчик):", h.lastText());
        h.say("Java разработчик");
        assertTrue(h.lastText().startsWith("Введите регион поиска"));
        h.press("sub_reg_" + SPB);
        assertTrue(h.lastText().startsWith("Укажите минимальную зарплату"));
        h.press("sub_sal_80000");
        assertEquals("Выберите требуемый опыт работы:", h.lastText());
        h.press("sub_exp_1_3");
        assertEquals("Выберите график работы:", h.lastText());
        h.press("sub_sch_full");
        assertEquals("Выберите тип занятости:", h.lastText());
        h.press("sub_emp_remote");
        assertTrue(h.lastText().startsWith("Введите ключевые слова"));
        h.say("Spring, SQL ,Kafka");

        String summary = h.lastText();
        assertTrue(summary.contains("• Должность: Java разработчик"));
        assertTrue(summary.contains("• Регион: Санкт-Петербург"));
        assertTrue(summary.contains("• Мин. зарплата: 80 000 руб."));
        assertTrue(summary.contains("• Опыт: 1–3 года"));
        assertTrue(summary.contains("• График: Полный рабочий день"));
        assertTrue(summary.contains("• Занятость: Дистанционная (удалённая) работа"));
        assertTrue(summary.contains("• Ключевые слова: Spring, SQL, Kafka"));
        assertTrue(summary.endsWith("Создать подписку с этими параметрами?"));
        assertTrue(h.tg.last().callbacks().containsAll(java.util.List.of("sub_confirm", "sub_cancel")));

        h.press("sub_confirm");

        assertEquals("Подписка создана", h.tg.lastAnswer());
        assertEquals("Подписка успешно создана!", h.lastText());
        var saved = h.subs.list(Harness.UID);
        assertEquals(1, saved.size());
        assertEquals(new SubscriptionCriteria("Java разработчик", SPB, 80_000, "1_3", "full", "remote",
                "Spring, SQL, Kafka"), saved.get(0).criteria());
        assertTrue(saved.get(0).active());
    }

    @Test
    void everyAnsweredQuestionLosesItsButtons() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("QA");
        var regionPrompt = h.tg.messages.stream().filter(m -> m.text.startsWith("Введите регион")).findFirst().orElseThrow();
        assertFalse(regionPrompt.callbacks().isEmpty());

        h.press("sub_reg_" + MOSCOW);

        assertTrue(regionPrompt.callbacks().isEmpty(), "кнопки у предыдущего вопроса должны исчезнуть");
        var titlePrompt = h.tg.messages.get(0);
        assertTrue(titlePrompt.callbacks().isEmpty());
    }

    @Test
    void skippingAllQuestions_asksForAtLeastOneParameterAndRestarts() {
        Harness h = new Harness();
        h.say("/subscribe");
        for (int i = 0; i < 7; i++) {
            h.press("sub_skip");
        }

        assertTrue(h.texts().contains(MessageFormatter.NEED_ONE_PARAM));
        assertEquals("Введите название вакансии (например: Java разработчик):", h.lastText());
        assertTrue(h.subs.list(Harness.UID).isEmpty());
        assertEquals("Вопрос пропущен", h.tg.lastAnswer());
    }

    @Test
    void skippedParametersAreShownAsNotImportant_andSavedAsNull() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("Python");
        for (int i = 0; i < 6; i++) {
            h.press("sub_skip");
        }

        assertTrue(h.lastText().contains("• Регион: не важно"));
        assertTrue(h.lastText().contains("• Опыт: не важно"));
        h.press("sub_confirm");
        assertEquals(new SubscriptionCriteria("Python", null, null, null, null, null, null),
                h.subs.list(Harness.UID).get(0).criteria());
    }

    @Test
    void unknownRegion_repeatsQuestionWithHint_thenAcceptsCaseInsensitiveName() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("Java");
        int before = h.tg.messages.size();

        h.say("Атлантида");

        assertEquals(before + 1, h.tg.messages.size());
        assertTrue(h.lastText().startsWith(MessageFormatter.NOTHING_FOUND_REGION));
        assertTrue(h.lastText().contains("Введите регион поиска"));

        h.say("  санкт-петербург ");
        assertTrue(h.lastText().startsWith("Укажите минимальную зарплату"));
    }

    @Test
    void invalidSalary_keepsStateAndShowsHint_zeroMeansSkip() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("QA");
        h.press("sub_skip"); // регион

        h.say("abc");
        assertTrue(h.lastText().startsWith("Введите целое число, например: 80000"));
        assertTrue(h.lastText().contains("Укажите минимальную зарплату"));
        h.say("-5");
        assertTrue(h.lastText().startsWith("Введите целое число"));
        h.say("0");
        assertEquals("Выберите требуемый опыт работы:", h.lastText());

        h.press("sub_skip");
        h.press("sub_skip");
        h.press("sub_skip");
        h.press("sub_skip");
        assertTrue(h.lastText().contains("• Мин. зарплата: не важно"));
    }

    @Test
    void salaryWithSpacesIsAccepted() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("QA");
        h.press("sub_skip");
        h.say("80 000");
        for (int i = 0; i < 4; i++) {
            h.press("sub_skip");
        }
        assertTrue(h.lastText().contains("• Мин. зарплата: 80 000 руб."));
    }

    @Test
    void textInsteadOfButton_remindsToUseButtons_andStateIsKept() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("QA");
        h.press("sub_skip");
        h.press("sub_skip");
        assertEquals("Выберите требуемый опыт работы:", h.lastText());

        h.say("много");
        assertTrue(h.lastText().startsWith(MessageFormatter.USE_BUTTONS));
        assertTrue(h.lastText().contains("Выберите требуемый опыт работы:"));

        h.press("sub_exp_none");
        assertEquals("Выберите график работы:", h.lastText());
    }

    @Test
    void tooLongTitle_isRejected() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("x".repeat(101));
        assertTrue(h.lastText().startsWith("Название должно быть от 1 до 100 символов."));
        h.say("x".repeat(100));
        assertTrue(h.lastText().startsWith("Введите регион поиска"));
    }

    @Test
    void staleButtonFromOldMessage_isIgnored() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("QA");
        var regionPrompt = h.tg.messages.stream().filter(m -> m.text.startsWith("Введите регион")).findFirst().orElseThrow();
        h.press("sub_reg_" + SPB);

        h.pressOn(regionPrompt, "sub_reg_" + MOSCOW);

        assertEquals("Кнопка устарела. Начните заново: /subscribe", h.tg.lastAnswer());
        for (int i = 0; i < 5; i++) {
            h.press("sub_skip");
        }
        h.press("sub_confirm");
        assertEquals(SPB, h.subs.list(Harness.UID).get(0).criteria().regionCode());
    }

    @Test
    void unknownButtonValue_isRejectedWithoutChangingState() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("QA");
        h.press("sub_skip");
        h.press("sub_skip");
        var experiencePrompt = h.tg.last();

        h.pressOn(experiencePrompt, "sub_exp_forever");

        assertEquals("Неизвестный вариант", h.tg.lastAnswer());
        assertEquals(experiencePrompt, h.tg.last());
    }

    @Test
    void cancel_savesNothingAndResetsDialog() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("QA");
        for (int i = 0; i < 6; i++) {
            h.press("sub_skip");
        }

        h.press("sub_cancel");

        assertEquals("Создание подписки отменено", h.tg.lastAnswer());
        assertEquals("Создание подписки отменено.", h.lastText());
        assertTrue(h.subs.list(Harness.UID).isEmpty());
        h.say("что-то");
        assertEquals(MessageFormatter.UNKNOWN_INPUT, h.lastText());
    }

    @Test
    void textOnConfirmStep_remindsAboutButtons() {
        Harness h = new Harness();
        h.say("/subscribe");
        h.say("QA");
        for (int i = 0; i < 6; i++) {
            h.press("sub_skip");
        }
        h.say("да");
        assertTrue(h.lastText().startsWith(MessageFormatter.CONFIRM_WITH_BUTTONS));
        assertTrue(h.tg.last().callbacks().contains("sub_confirm"));
    }

    @Test
    void anyCommandInterruptsWizard_andRemovesItsButtons() {
        Harness h = new Harness();
        h.say("/subscribe");
        var titlePrompt = h.tg.last();

        h.say("/help");

        assertTrue(titlePrompt.callbacks().isEmpty());
        assertTrue(h.lastText().contains("Поддержка: @jobfinder_support"));
        h.say("Java");
        assertEquals(MessageFormatter.UNKNOWN_INPUT, h.lastText());
    }

    @Test
    void serviceFailureOnConfirm_keepsDialogSoUserCanRetry() {
        var flaky = new StubSubscriptionService() {
            private boolean failed;

            @Override
            public synchronized ru.vacancybot.bot.domain.Subscription create(
                    long telegramId, SubscriptionCriteria criteria) {
                if (!failed) {
                    failed = true;
                    throw new IllegalStateException("service is down");
                }
                return super.create(telegramId, criteria);
            }
        };
        Harness h = new Harness(flaky);
        h.say("/subscribe");
        h.say("QA");
        for (int i = 0; i < 6; i++) {
            h.press("sub_skip");
        }
        var summary = h.tg.last();

        h.press("sub_confirm");

        assertEquals(MessageFormatter.SERVICE_ERROR, h.tg.lastAnswer());
        assertTrue(summary.callbacks().contains("sub_confirm"), "кнопки остаются для повтора");
        assertTrue(h.subs.list(Harness.UID).isEmpty());

        h.press("sub_confirm");
        assertEquals("Подписка успешно создана!", h.lastText());
        assertEquals(1, h.subs.list(Harness.UID).size());
        assertNotNull(h.subs.list(Harness.UID).get(0));
    }
}
