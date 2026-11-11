package ru.vacancybot.worker.client.dbservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.vacancybot.worker.config.SyncProperties;
import ru.vacancybot.worker.dto.dbservice.ChangeKey;
import ru.vacancybot.worker.dto.dbservice.RegionDto;
import ru.vacancybot.worker.dto.dbservice.SyncStateDto;
import ru.vacancybot.worker.dto.dbservice.VacancyChangeDto;
import ru.vacancybot.worker.dto.dbservice.VacancyUpsertDto;
import ru.vacancybot.worker.exception.DbServiceException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Component
public class DbServiceClient {

    public static final String CURSOR_KEY = "vacancies.cursor";

    public static final int MAX_SYNC_BATCH = 100;
    public static final int MAX_UNPUBLISHED_LIMIT = 1000;
    public static final int MAX_PUBLISHED_KEYS = 1000;

    private static final int MAX_ERROR_LENGTH = 500;
    private static final int ERROR_BODY_LIMIT = 500;

    private final RestClient restClient;
    private final SyncProperties syncProperties;

    public DbServiceClient(@Qualifier("dbServiceRestClient") RestClient restClient,
                           SyncProperties syncProperties) {
        this.restClient = restClient;
        this.syncProperties = syncProperties;
    }

    public List<VacancyChangeDto> sync(List<VacancyUpsertDto> vacancies) {
        if (vacancies.isEmpty()) {
            return List.of();
        }
        if (vacancies.size() > MAX_SYNC_BATCH) {
            throw new IllegalArgumentException(
                    "sync accepts at most " + MAX_SYNC_BATCH + " vacancies, got " + vacancies.size());
        }
        return execute("sync", () -> {
            List<VacancyChangeDto> body = withErrorHandling(
                    restClient.post()
                            .uri("/api/vacancies/sync")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(vacancies))
                    .body(new ParameterizedTypeReference<>() {});
            return body == null ? List.of() : body;
        });
    }

    public List<VacancyChangeDto> syncAll(List<VacancyUpsertDto> vacancies) {
        List<VacancyChangeDto> result = new ArrayList<>();
        for (int from = 0; from < vacancies.size(); from += MAX_SYNC_BATCH) {
            int to = Math.min(from + MAX_SYNC_BATCH, vacancies.size());
            result.addAll(sync(vacancies.subList(from, to)));
        }
        return result;
    }

    public Optional<Instant> getLastModified() {
        String body = execute("last-modified", () -> withErrorHandling(
                restClient.get().uri("/api/vacancies/last-modified"))
                .body(String.class));
        if (body == null) {
            return Optional.empty();
        }

        String value = body.trim().replace("\"", "");
        if (value.isBlank() || value.equalsIgnoreCase("null")) {
            return Optional.empty();
        }
        return Optional.of(parseInstant(value));
    }


    public Optional<Instant> getCursor() {
        return getSyncState(CURSOR_KEY).map(state -> parseInstant(state.value()));
    }

    public void saveCursor(Instant cursor) {
        String value = DateTimeFormatter.ISO_INSTANT.format(cursor.truncatedTo(ChronoUnit.SECONDS));
        putSyncState(CURSOR_KEY, value);
    }

    public Optional<SyncStateDto> getSyncState(String key) {
        return execute("get sync-state", () -> restClient.get()
                .uri("/api/sync-state/{key}", key)
                .exchange((request, response) -> {
                    int code = response.getStatusCode().value();
                    if (code == 404) {
                        return Optional.empty();
                    }
                    if (response.getStatusCode().isError()) {
                        throw failure(code, readBodySnippet(response));
                    }
                    return Optional.ofNullable(response.bodyTo(SyncStateDto.class));
                }));
    }

    public void putSyncState(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("sync-state value must not be blank");
        }
        execute("put sync-state", () -> {
            withErrorHandling(restClient.put()
                    .uri("/api/sync-state/{key}", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SyncStateRequest(value)))
                    .toBodilessEntity();
            return null;
        });
    }


    public List<VacancyChangeDto> fetchUnpublished(int limit) {
        if (limit < 1 || limit > MAX_UNPUBLISHED_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_UNPUBLISHED_LIMIT);
        }
        return execute("unpublished", () -> {
            List<VacancyChangeDto> body = withErrorHandling(
                    restClient.get().uri(uriBuilder -> uriBuilder
                            .path("/api/changes/unpublished")
                            .queryParam("limit", limit)
                            .build()))
                    .body(new ParameterizedTypeReference<>() {
                    });
            return body == null ? List.of() : body;
        });
    }

    public int markPublished(List<ChangeKey> keys) {
        int marked = 0;
        for (int from = 0; from < keys.size(); from += MAX_PUBLISHED_KEYS) {
            List<ChangeKey> chunk = keys.subList(from, Math.min(from + MAX_PUBLISHED_KEYS, keys.size()));
            MarkedResponse response = execute("published", () -> withErrorHandling(
                    restClient.post()
                            .uri("/api/changes/published")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(chunk))
                    .body(MarkedResponse.class));
            if (response != null && response.marked() != null) {
                marked += response.marked();
            }
        }
        return marked;
    }

    public int markPublishFailed(ChangeKey key, String error) {
        PublishFailedRequest request =
                new PublishFailedRequest(key.changeId(), key.changedAt(), truncate(error));
        UpdatedResponse response = execute("publish-failed", () -> withErrorHandling(
                restClient.post()
                        .uri("/api/changes/publish-failed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request))
                .body(UpdatedResponse.class));
        return response == null || response.updated() == null ? 0 : response.updated();
    }


    /** Полная замена справочника регионов ({@code PUT /api/regions}). Пустой список не допускается. */
    public void replaceRegions(List<RegionDto> regions) {
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("Regions list must not be empty: it would erase the dictionary");
        }
        execute("regions", () -> {
            withErrorHandling(restClient.put()
                    .uri("/api/regions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(regions))
                    .toBodilessEntity();
            return null;
        });
    }


    private <T> T execute(String operation, Supplier<T> call) {
        for (int attempt = 0; ; attempt++) {
            DbServiceException failure;
            try {
                return call.get();
            } catch (DbServiceException e) {
                failure = e;
            } catch (ResourceAccessException e) {
                // таймаут чтения или соединения, обрыв сети
                failure = new DbServiceException(
                        operation + ": db-service is unreachable or timed out", e, true);
            } catch (RestClientException e) {
                // например, тело ответа не соответствует DTO: повтор не поможет
                throw new DbServiceException(
                        operation + ": cannot process db-service response", e, false);
            }

            if (!failure.isRetryable() || attempt >= syncProperties.maxRetries()) {
                throw failure;
            }
            long delay = syncProperties.retryBaseDelay().toMillis() * (1L << attempt);
            log.warn("db-service call '{}' failed (attempt {}): {}. Retrying in {} ms",
                    operation, attempt + 1, failure.getMessage(), delay);
            sleep(delay);
        }
    }

    private RestClient.ResponseSpec withErrorHandling(RestClient.RequestHeadersSpec<?> spec) {
        return spec.retrieve().onStatus(HttpStatusCode::isError, this::throwForStatus);
    }

    private void throwForStatus(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw failure(response.getStatusCode().value(), readBodySnippet(response));
    }

    private static DbServiceException failure(int code, String body) {
        return new DbServiceException(
                "db-service returned HTTP " + code + (body.isBlank() ? "" : ": " + body),
                code,
                code >= 500 || code == 429);
    }

    private static String readBodySnippet(ClientHttpResponse response) {
        try {
            byte[] bytes = response.getBody().readNBytes(ERROR_BODY_LIMIT);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static Instant parseInstant(String value) {
        try {
            return OffsetDateTime.parse(value.trim()).toInstant();
        } catch (DateTimeParseException e) {
            throw new DbServiceException("Unexpected date format from db-service: " + value, e, false);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", e);
        }
    }

    public record SyncStateRequest(String value) {}

    public record PublishFailedRequest(long changeId, Instant changedAt, String error) {}

    public record MarkedResponse(Integer marked) {}

    public record UpdatedResponse(Integer updated) {}
}