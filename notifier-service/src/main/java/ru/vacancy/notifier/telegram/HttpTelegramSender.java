package ru.vacancy.notifier.telegram;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** HTTP-слой поверх java.net.http. Разбор ответов вынесен в {@link TelegramResponses}. */
public final class HttpTelegramSender implements TelegramSender {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI uri;

    public HttpTelegramSender(String token, HttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
        this.uri = URI.create("https://api.telegram.org/bot" + token + "/sendMessage");
    }

    @Override
    public void sendMessage(long chatId, String text) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", text);
        payload.put("parse_mode", "HTML");
        payload.put("link_preview_options", Map.of("is_disabled", true));

        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new PermanentException("не удалось сериализовать запрос");
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // только имя класса: в тексте ошибки может оказаться URL с токеном
            throw new TemporaryException("network error: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("прервано во время отправки", e);
        }

        Map<String, Object> body;
        try {
            body = mapper.readValue(response.body(), MAP_TYPE);
        } catch (Exception e) {
            body = Map.of();
        }
        TelegramResponses.check(response.statusCode(), body);
    }
}
