package ru.vacancy.notifier.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class InlineKeyboardTest {

    private static final UUID ID = UUID.fromString("3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d");

    @Test
    void newVacancyActionsUseTheCallbackContractOfTheBot() {
        InlineKeyboard keyboard = InlineKeyboard.newVacancyActions(ID);

        assertEquals(1, keyboard.rows().size());
        assertEquals("👎 Не интересно", keyboard.rows().get(0).get(0).text());
        assertEquals("vac_skip_" + ID, keyboard.rows().get(0).get(0).callbackData());
        assertEquals("👍 В избранное", keyboard.rows().get(0).get(1).text());
        assertEquals("fav_add_" + ID, keyboard.rows().get(0).get(1).callbackData());
    }

    @Test
    void callbackDataFitsTelegramLimit() {
        for (InlineKeyboard.Button b : InlineKeyboard.newVacancyActions(ID).rows().get(0)) {
            assertTrue(b.callbackData().getBytes(StandardCharsets.UTF_8).length <= 64);
        }
        assertThrows(IllegalArgumentException.class, () -> new InlineKeyboard.Button("x", "a".repeat(65)));
    }

    @Test
    void payloadHasTelegramFormat() throws Exception {
        String json = new ObjectMapper().writeValueAsString(InlineKeyboard.newVacancyActions(ID).toPayload());
        assertTrue(json.startsWith("{\"inline_keyboard\":[[{\"text\":\"👎 Не интересно\",\"callback_data\":\"vac_skip_"));
        assertTrue(json.contains("\"callback_data\":\"fav_add_" + ID + "\""));
    }

    @Test
    void sendMessagePayloadIncludesReplyMarkupOnlyWhenThereIsAKeyboard() {
        Map<String, Object> plain = HttpTelegramSender.payload(5, "текст", null);
        assertFalse(plain.containsKey("reply_markup"));
        assertEquals("HTML", plain.get("parse_mode"));

        Map<String, Object> withButtons = HttpTelegramSender.payload(5, "текст", InlineKeyboard.newVacancyActions(ID));
        assertTrue(withButtons.containsKey("reply_markup"));
        assertEquals(List.of("text", "callback_data"),
                ((Map<?, ?>) ((List<?>) ((List<?>) ((Map<?, ?>) withButtons.get("reply_markup"))
                        .get("inline_keyboard")).get(0)).get(0)).keySet().stream().map(Object::toString).toList());
    }
}
