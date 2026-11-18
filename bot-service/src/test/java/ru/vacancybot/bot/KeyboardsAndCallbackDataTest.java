package ru.vacancybot.bot;

import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.fsm.DialogState;
import ru.vacancybot.bot.domain.Favorite;
import ru.vacancybot.bot.domain.FavoriteStatus;
import ru.vacancybot.bot.domain.Subscription;
import ru.vacancybot.bot.domain.SubscriptionCriteria;
import ru.vacancybot.bot.stub.SampleVacancies;
import ru.vacancybot.bot.stub.StubRegionClassifier;
import ru.vacancybot.bot.telegram.dto.InlineKeyboardButton;
import ru.vacancybot.bot.telegram.dto.InlineKeyboardMarkup;
import ru.vacancybot.bot.view.CallbackData;
import ru.vacancybot.bot.view.Keyboards;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyboardsAndCallbackDataTest {

    private static List<String> callbacks(InlineKeyboardMarkup m) {
        List<String> result = new ArrayList<>();
        for (List<InlineKeyboardButton> row : m.inlineKeyboard()) {
            for (InlineKeyboardButton b : row) {
                result.add(b.callbackData());
            }
        }
        return result;
    }

    @Test
    void everyCallbackDataFitsTelegramLimitOf64Bytes() {
        List<InlineKeyboardMarkup> all = new ArrayList<>();
        for (DialogState st : DialogState.values()) {
            if (st != DialogState.IDLE) {
                all.add(Keyboards.forState(st, new StubRegionClassifier().popular()));
            }
        }
        var sub = new Subscription(Long.MAX_VALUE, new SubscriptionCriteria(null, null, null, null, null, null, null), true);
        all.add(Keyboards.subscriptionCard(sub));
        all.add(Keyboards.favoriteCard(new Favorite(SampleVacancies.NEW_VACANCY, FavoriteStatus.SAVED)));
        all.add(Keyboards.favoriteStatuses(UUID.randomUUID().toString()));
        all.add(Keyboards.newVacancy(UUID.randomUUID().toString(), Long.MAX_VALUE));

        for (InlineKeyboardMarkup m : all) {
            for (String data : callbacks(m)) {
                assertTrue(data.getBytes(StandardCharsets.UTF_8).length <= 64, data);
            }
        }
    }

    @Test
    void questionKeyboards_matchSpecification() {
        var regions = new StubRegionClassifier().popular();
        assertEquals(List.of("sub_skip"), callbacks(Keyboards.forState(DialogState.SEARCH_TITLE, regions)));
        assertEquals(List.of("sub_reg_7700000000000", "sub_reg_7800000000000", "sub_skip"),
                callbacks(Keyboards.forState(DialogState.SEARCH_REGION, regions)));
        assertEquals(List.of("sub_sal_50000", "sub_sal_80000", "sub_sal_100000", "sub_sal_150000", "sub_skip"),
                callbacks(Keyboards.forState(DialogState.SEARCH_SALARY, regions)));
        assertEquals(List.of("sub_exp_none", "sub_exp_1_3", "sub_exp_3_6", "sub_exp_6_plus", "sub_skip"),
                callbacks(Keyboards.forState(DialogState.SEARCH_EXPERIENCE, regions)));
        assertEquals(List.of("sub_sch_rotation", "sub_sch_flex", "sub_sch_shift", "sub_sch_irregular",
                        "sub_sch_part", "sub_sch_full", "sub_skip"),
                callbacks(Keyboards.forState(DialogState.SEARCH_SCHEDULE, regions)));
        assertEquals(List.of("sub_emp_rotation", "sub_emp_remote", "sub_emp_temp", "sub_emp_intern", "sub_skip"),
                callbacks(Keyboards.forState(DialogState.SEARCH_EMPLOYMENT, regions)));
        assertEquals(List.of("sub_skip"), callbacks(Keyboards.forState(DialogState.SEARCH_KEYWORDS, regions)));
        assertEquals(List.of("sub_confirm", "sub_cancel"),
                callbacks(Keyboards.forState(DialogState.CONFIRM_SUBSCRIPTION, regions)));
    }

    @Test
    void without_removesMatchingButtonsAndEmptyRows() {
        var markup = Keyboards.newVacancy(UUID.randomUUID().toString(), 5);
        var onlyFav = Keyboards.without(markup, d -> d.startsWith("sub_"));
        assertEquals(1, callbacks(onlyFav).size());
        assertTrue(callbacks(onlyFav).get(0).startsWith("fav_add_"));

        assertTrue(Keyboards.without(markup, d -> true).isEmpty());
        assertTrue(Keyboards.without(null, d -> true).isEmpty());
    }

    @Test
    void callbackData_argAndRouting() {
        assertEquals("1_3", CallbackData.arg("sub_exp_1_3", CallbackData.SUB_EXP));
        assertNull(CallbackData.arg("fav_add_1", CallbackData.SUB_EXP));
        assertNull(CallbackData.arg(null, CallbackData.SUB_EXP));

        for (String wizard : List.of("sub_skip", "sub_confirm", "sub_cancel", "sub_reg_1", "sub_sal_5",
                "sub_exp_none", "sub_sch_full", "sub_emp_temp")) {
            assertTrue(CallbackData.isWizard(wizard), wizard);
        }
        for (String other : List.of("sub_del_1", "sub_pause_1", "sub_resume_1", "sub_edit_1", "fav_add_x", "")) {
            assertFalse(CallbackData.isWizard(other), other);
        }
        assertFalse(CallbackData.isWizard(null));
    }
}
