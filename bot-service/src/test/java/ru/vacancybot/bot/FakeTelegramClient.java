package ru.vacancybot.bot;

import ru.vacancybot.bot.telegram.TelegramClient;
import ru.vacancybot.bot.telegram.dto.BotCommand;
import ru.vacancybot.bot.telegram.dto.InlineKeyboardButton;
import ru.vacancybot.bot.telegram.dto.InlineKeyboardMarkup;

import java.util.ArrayList;
import java.util.List;

/** Фейк Telegram: запоминает сообщения и ответы на callback, правки применяет как настоящий Telegram. */
public class FakeTelegramClient implements TelegramClient {

    public static class Msg {
        public final int id;
        public final long chatId;
        public String text;
        public InlineKeyboardMarkup keyboard;

        Msg(int id, long chatId, String text, InlineKeyboardMarkup keyboard) {
            this.id = id;
            this.chatId = chatId;
            this.text = text;
            this.keyboard = keyboard == null ? InlineKeyboardMarkup.empty() : keyboard;
        }

        public List<String> callbacks() {
            List<String> result = new ArrayList<>();
            if (keyboard.inlineKeyboard() != null) {
                for (List<InlineKeyboardButton> row : keyboard.inlineKeyboard()) {
                    for (InlineKeyboardButton b : row) {
                        result.add(b.callbackData());
                    }
                }
            }
            return result;
        }
    }

    public final List<Msg> messages = new ArrayList<>();
    public final List<String> answers = new ArrayList<>();
    private int nextId = 100;

    @Override
    public int sendMessage(long chatId, String text, InlineKeyboardMarkup keyboard) {
        Msg m = new Msg(nextId++, chatId, text, keyboard);
        messages.add(m);
        return m.id;
    }

    @Override
    public void editMessageText(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        Msg m = byId(messageId);
        m.text = text;
        m.keyboard = keyboard == null ? InlineKeyboardMarkup.empty() : keyboard;
    }

    @Override
    public void editMessageReplyMarkup(long chatId, int messageId, InlineKeyboardMarkup keyboard) {
        byId(messageId).keyboard = keyboard == null ? InlineKeyboardMarkup.empty() : keyboard;
    }

    @Override
    public void answerCallbackQuery(String callbackQueryId, String text) {
        answers.add(text);
    }

    @Override
    public void setWebhook(String url, String secretToken) {
    }

    @Override
    public void setMyCommands(List<BotCommand> commands) {
    }

    public Msg byId(int id) {
        return messages.stream().filter(m -> m.id == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("No message " + id));
    }

    public Msg last() {
        return messages.get(messages.size() - 1);
    }

    public String lastAnswer() {
        return answers.isEmpty() ? null : answers.get(answers.size() - 1);
    }
}
