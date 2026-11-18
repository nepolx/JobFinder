package ru.vacancybot.bot.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.vacancybot.bot.config.BotProperties;
import ru.vacancybot.bot.telegram.dto.BotCommand;
import ru.vacancybot.bot.telegram.dto.InlineKeyboardMarkup;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реализация {@link TelegramClient} поверх Spring RestClient.
 * URL содержит токен бота, поэтому он никогда не попадает в сообщения исключений и логи
 * (исходные RestClientException с URL не пробрасываются).
 */
@Component
public class RestTelegramClient implements TelegramClient {

    private final RestClient http;
    private final ObjectMapper mapper;
    private final String apiBase;

    public RestTelegramClient(BotProperties props, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(10_000);
        this.http = RestClient.builder().requestFactory(factory).build();
        this.mapper = mapper;
        this.apiBase = props.apiBaseUrl() + "/bot" + props.token() + "/";
    }

    @Override
    public int sendMessage(long chatId, String text, InlineKeyboardMarkup keyboard) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        if (keyboard != null && !keyboard.isEmpty()) {
            body.put("reply_markup", keyboard);
        }
        return call("sendMessage", body).path("message_id").asInt();
    }

    @Override
    public void editMessageText(long chatId, int messageId, String text, InlineKeyboardMarkup keyboard) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("text", text);
        body.put("reply_markup", keyboard == null ? InlineKeyboardMarkup.empty() : keyboard);
        callIgnoringNotModified("editMessageText", body);
    }

    @Override
    public void editMessageReplyMarkup(long chatId, int messageId, InlineKeyboardMarkup keyboard) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", keyboard == null ? InlineKeyboardMarkup.empty() : keyboard);
        callIgnoringNotModified("editMessageReplyMarkup", body);
    }

    @Override
    public void answerCallbackQuery(String callbackQueryId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_query_id", callbackQueryId);
        if (text != null) {
            body.put("text", text);
        }
        call("answerCallbackQuery", body);
    }

    @Override
    public void setWebhook(String url, String secretToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        if (secretToken != null && !secretToken.isBlank()) {
            body.put("secret_token", secretToken);
        }
        body.put("allowed_updates", List.of("message", "callback_query"));
        call("setWebhook", body);
    }

    @Override
    public void setMyCommands(List<BotCommand> commands) {
        call("setMyCommands", Map.of("commands", commands));
    }

    private void callIgnoringNotModified(String method, Map<String, Object> body) {
        try {
            call(method, body);
        } catch (TelegramApiException e) {
            if (e.getMessage() == null || !e.getMessage().contains("message is not modified")) {
                throw e;
            }
        }
    }

    private JsonNode call(String method, Map<String, Object> body) {
        try {
            JsonNode response = http.post()
                    .uri(URI.create(apiBase + method))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.path("ok").asBoolean(false)) {
                throw new TelegramApiException(0, method + ": empty or negative response", null);
            }
            return response.path("result");
        } catch (RestClientResponseException e) {
            throw toApiException(method, e);
        } catch (RestClientException e) {
            // намеренно без cause: в сообщении исходного исключения есть URL с токеном
            throw new TelegramApiException(0, method + " failed: " + e.getClass().getSimpleName(), null);
        }
    }

    private TelegramApiException toApiException(String method, RestClientResponseException e) {
        int code = e.getStatusCode().value();
        String description = method + " failed";
        Integer retryAfter = null;
        try {
            JsonNode json = mapper.readTree(e.getResponseBodyAsString());
            description = json.path("description").asText(description);
            if (json.path("parameters").hasNonNull("retry_after")) {
                retryAfter = json.path("parameters").path("retry_after").asInt();
            }
        } catch (Exception ignored) {
            // тело не JSON — оставляем общее описание
        }
        return new TelegramApiException(code, description, retryAfter);
    }
}
