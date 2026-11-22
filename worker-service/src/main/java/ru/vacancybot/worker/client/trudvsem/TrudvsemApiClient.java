package ru.vacancybot.worker.client.trudvsem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.vacancybot.worker.dto.external.TrudvsemApiResponse;
import ru.vacancybot.worker.dto.external.TrudvsemVacancyDto;
import ru.vacancybot.worker.exception.TrudvsemApiException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrudvsemApiClient {

    public static final int MAX_LIMIT = 100;

    public static final int MAX_PAGE = 99;

    private static final int ERROR_BODY_LIMIT = 500;

    private final RestClient trudvsemRestClient;
    private final TrudvsemApiProperties properties;

    public VacancyPage fetchPage(int page, Instant modifiedFrom, Instant modifiedTo) {
        return fetchPage(page, properties.pageSize(), modifiedFrom, modifiedTo);
    }

    public VacancyPage fetchPage(int page, int limit, Instant modifiedFrom, Instant modifiedTo) {
        if (page < 0 || page > MAX_PAGE) {
            throw new IllegalArgumentException(
                    "Page (offset) must be between 0 and " + MAX_PAGE
                            + ": the API returns HTTP 500 for offset >= " + (MAX_PAGE + 1));
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
        Objects.requireNonNull(modifiedFrom, "modifiedFrom must not be null");

        Instant from = modifiedFrom.truncatedTo(ChronoUnit.SECONDS);
        Instant to = modifiedTo == null ? null : modifiedTo.truncatedTo(ChronoUnit.SECONDS);
        if (to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("modifiedTo must not be before modifiedFrom");
        }

        String fromValue = DateTimeFormatter.ISO_INSTANT.format(from);
        String toValue = to == null ? null : DateTimeFormatter.ISO_INSTANT.format(to);
        log.debug("Requesting vacancies: page={}, limit={}, modifiedFrom={}, modifiedTo={}",
                page, limit, fromValue, toValue);

        TrudvsemApiResponse response;
        try {
            response = trudvsemRestClient
                    .get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/v1/vacancies")
                                .queryParam("offset", page)
                                .queryParam("limit", limit)
                                .queryParam("modifiedFrom", fromValue);
                        if (toValue != null) {
                            uriBuilder.queryParam("modifiedTo", toValue);
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        int code = httpResponse.getStatusCode().value();
                        String body = readBodySnippet(httpResponse);
                        throw new TrudvsemApiException(
                                "Trudvsem API returned HTTP " + code + (body.isBlank() ? "" : ": " + body),
                                code,
                                isRetryableHttpStatus(code));
                    })
                    .body(TrudvsemApiResponse.class);
        } catch (ResourceAccessException e) {
            throw new TrudvsemApiException("Trudvsem API is unreachable or timed out", e, true);
        } catch (RestClientException e) {
            throw new TrudvsemApiException("Failed to read response of Trudvsem API", e, false);
        }
        return toPage(response, page, limit);
    }

    public Optional<Instant> findLatestModified(Instant modifiedFrom) {
        VacancyPage page = fetchPage(0, 1, modifiedFrom, null);
        if (page.isEmpty()) {
            return Optional.empty();
        }
        String dateModify = page.vacancies().getFirst().dateModify();
        try {
            return Optional.of(TrudvsemDateParser.parse(dateModify));
        } catch (IllegalArgumentException e) {
            throw new TrudvsemApiException("Cannot parse date_modify: " + dateModify, e, false);
        }
    }

    private VacancyPage toPage(TrudvsemApiResponse response, int page, int limit) {
        if (response == null) {
            throw new TrudvsemApiException("Trudvsem API returned empty response", (Integer) null, true);
        }
        if (!"200".equals(response.status())) {
            String details = response.meta() == null ? null : response.meta().error();
            throw new TrudvsemApiException(
                    "Trudvsem API returned status " + response.status()
                            + (details == null || details.isBlank() ? "" : ": " + details),
                    (Integer) null,
                    response.status() != null && response.status().startsWith("5"));
        }
        if (response.meta() == null || response.meta().total() == null) {
            throw new TrudvsemApiException("Trudvsem API response does not contain meta.total", (Integer) null, false);
        }

        List<TrudvsemVacancyDto> vacancies =
                response.results() == null || response.results().vacancies() == null
                        ? List.of()
                        : response.results().vacancies().stream()
                        .filter(Objects::nonNull)
                        .map(TrudvsemApiResponse.VacancyWrapper::vacancy)
                        .filter(Objects::nonNull)
                        .toList();

        int total = response.meta().total();
        log.debug("Received {} vacancies on page {}. Total in window: {}", vacancies.size(), page, total);
        return new VacancyPage(vacancies, total, page, limit);
    }

    private static boolean isRetryableHttpStatus(int code) {
        return code >= 500 || code == 429;
    }

    private static String readBodySnippet(ClientHttpResponse response) {
        try {
            byte[] bytes = response.getBody().readNBytes(ERROR_BODY_LIMIT);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    public record VacancyPage(List<TrudvsemVacancyDto> vacancies, int total, int page, int limit) {

        public boolean isEmpty() {
            return vacancies.isEmpty();
        }

        public int nextPage() {
            return page + 1;
        }

        public int totalPages() {
            return (int) (((long) total + limit - 1) / limit);
        }

        public boolean hasNext() {
            return nextPage() < totalPages() && nextPage() <= MAX_PAGE;
        }

        public boolean exceedsReadableRange() {
            return totalPages() > MAX_PAGE + 1;
        }
    }
}