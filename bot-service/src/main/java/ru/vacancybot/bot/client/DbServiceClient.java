package ru.vacancybot.bot.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Тонкий HTTP-клиент к db-service (JDK HttpClient + Jackson, без Spring).
 * Любой ответ не 2xx превращается в {@link DbServiceException}; 404 можно получить как Optional.empty().
 */
public class DbServiceClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final String baseUrl;
    private final ObjectMapper mapper;

    public DbServiceClient(String baseUrl, ObjectMapper mapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.mapper = mapper;
    }

    /** Запрос, ответ разбирается в T. */
    public <T> T call(String method, String path, Object body, TypeReference<T> type) {
        HttpResponse<String> response = execute(method, path, body);
        ensureSuccess(response);
        try {
            return mapper.readValue(response.body(), type);
        } catch (IOException e) {
            throw new DbServiceException(response.statusCode(), "Unexpected db-service response for " + method + " " + path);
        }
    }

    /** Запрос, тело ответа не нужно. */
    public void call(String method, String path, Object body) {
        ensureSuccess(execute(method, path, body));
    }

    /** Как {@link #call(String, String, Object, TypeReference)}, но 404 даёт пустой Optional. */
    public <T> Optional<T> callOptional(String method, String path, Object body, TypeReference<T> type) {
        HttpResponse<String> response = execute(method, path, body);
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        ensureSuccess(response);
        try {
            return Optional.of(mapper.readValue(response.body(), type));
        } catch (IOException e) {
            throw new DbServiceException(response.statusCode(), "Unexpected db-service response for " + method + " " + path);
        }
    }

    /** Кодирует значение для сегмента пути. */
    public static String segment(Object value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private HttpResponse<String> execute(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json");
            HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
            if (body != null) {
                builder.header("Content-Type", "application/json");
                publisher = HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8);
            }
            return http.send(builder.method(method, publisher).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DbServiceException(0, "db-service call interrupted: " + method + " " + path);
        } catch (IOException e) {
            throw new DbServiceException(0, "db-service unreachable (" + e.getClass().getSimpleName() + "): " + method + " " + path);
        }
    }

    private static void ensureSuccess(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String body = response.body() == null ? "" : response.body();
            throw new DbServiceException(status, "db-service returned " + status + ": "
                    + (body.length() > 300 ? body.substring(0, 300) : body));
        }
    }
}
