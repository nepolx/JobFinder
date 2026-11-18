package ru.vacancybot.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.telegram.dto.InlineKeyboardButton;
import ru.vacancybot.bot.telegram.dto.InlineKeyboardMarkup;
import ru.vacancybot.bot.telegram.dto.Update;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesCallbackQueryUpdateAsSentByTelegram() throws Exception {
        String json = """
                {"update_id": 10, "callback_query": {"id": "4382", "chat_instance": "x",
                  "from": {"id": 42, "is_bot": false, "first_name": "Lina", "language_code": "ru"},
                  "message": {"message_id": 77, "date": 1700000000,
                    "from": {"id": 999, "is_bot": true, "first_name": "bot"},
                    "chat": {"id": 42, "type": "private", "first_name": "Lina"},
                    "text": "Выберите график работы:",
                    "reply_markup": {"inline_keyboard": [[{"text": "Полный рабочий день", "callback_data": "sub_sch_full"}]]}},
                  "data": "sub_sch_full"}}
                """;

        Update u = mapper.readValue(json, Update.class);

        assertEquals(10, u.updateId());
        assertNull(u.message());
        var q = u.callbackQuery();
        assertEquals("4382", q.id());
        assertEquals(42, q.from().id());
        assertFalse(q.from().isBot());
        assertEquals(77, q.message().messageId());
        assertEquals("private", q.message().chat().type());
        assertEquals("sub_sch_full", q.data());
        assertEquals("sub_sch_full", q.message().replyMarkup().inlineKeyboard().get(0).get(0).callbackData());
    }

    @Test
    void parsesTextMessageWithUnknownFields() throws Exception {
        String json = """
                {"update_id": 11, "message": {"message_id": 5, "date": 1, "entities": [{"type": "bot_command"}],
                  "from": {"id": 42, "is_bot": false}, "chat": {"id": 42, "type": "private"}, "text": "/start"}}
                """;
        Update u = mapper.readValue(json, Update.class);
        assertEquals("/start", u.message().text());
        assertNull(u.callbackQuery());
    }

    @Test
    void serializesKeyboardInTelegramFormat() throws Exception {
        var markup = new InlineKeyboardMarkup(List.of(List.of(new InlineKeyboardButton("Пропустить", "sub_skip"))));
        String json = mapper.writeValueAsString(markup);
        assertTrue(json.contains("\"inline_keyboard\""));
        assertTrue(json.contains("\"callback_data\":\"sub_skip\""));
        assertEquals("{\"inline_keyboard\":[]}", mapper.writeValueAsString(InlineKeyboardMarkup.empty()));
    }
}
