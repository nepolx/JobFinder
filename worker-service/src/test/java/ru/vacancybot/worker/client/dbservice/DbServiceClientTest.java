package ru.vacancybot.worker.client.dbservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.vacancybot.worker.config.SyncProperties;
import ru.vacancybot.common.dto.ChangeKeyDto;
import ru.vacancybot.common.dto.RegionDto;
import ru.vacancybot.common.dto.VacancyChangeDto;
import ru.vacancybot.common.dto.VacancyUpsertDto;
import ru.vacancybot.worker.exception.DbServiceException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DbServiceClientTest {

    private static final String BASE = "http://db.test";
    private static final String VACANCY_ID = "3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d";

    private MockRestServiceServer server;
    private DbServiceClient client;

    @BeforeEach
    void setUp() {
        client = clientWithRetries(2);
    }

    private DbServiceClient clientWithRetries(int maxRetries) {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE)
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(mapper));
                });
        server = MockRestServiceServer.bindTo(builder).build();
        SyncProperties properties = new SyncProperties(
                Duration.ofHours(1), 2, maxRetries, Duration.ofMillis(1), Duration.ZERO, 10_000, 3);
        return new DbServiceClient(builder.build(), properties);
    }

    private static VacancyUpsertDto upsert(String id) {
        return new VacancyUpsertDto(id, "ООО Тест", "2800000000000", "Амурская область", "Грузчик",
                40000, null, "Сменная работа", "shift", null, 0, "duty", "req",
                "https://trudvsem.ru/vacancy/card/1/" + id, Instant.parse("2026-11-09T00:59:13Z"));
    }

    private static List<VacancyUpsertDto> upserts(int count) {
        List<VacancyUpsertDto> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(upsert("id-" + i));
        }
        return list;
    }

    @Test
    void syncPostsBatchAndParsesChanges() {
        server.expect(requestTo(BASE + "/api/vacancies/sync"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(VACANCY_ID))
                .andExpect(jsonPath("$[0].employerName").value("ООО Тест"))
                .andExpect(jsonPath("$[0].modifiedAt").value("2026-11-09T00:59:13Z"))
                .andRespond(withSuccess("""
                        [{"changeId":1001,"changedAt":"2026-11-09T12:30:00.123456Z","vacancyId":"%s",
                          "changeType":"updated","changes":{"salary_min":[40000,50000]}}]
                        """.formatted(VACANCY_ID), MediaType.APPLICATION_JSON));

        List<VacancyChangeDto> changes = client.sync(List.of(upsert(VACANCY_ID)));

        assertThat(changes).hasSize(1);
        VacancyChangeDto change = changes.getFirst();
        assertThat(change.changeId()).isEqualTo(1001);
        assertThat(change.changedAt()).isEqualTo(Instant.parse("2026-11-09T12:30:00.123456Z"));
        assertThat(change.vacancyId()).isEqualTo(VACANCY_ID);
        assertThat(change.changeType()).isEqualTo("updated");
        assertThat(change.changes().get("salary_min")).containsExactly(40000, 50000);
        server.verify();
    }

    @Test
    void syncParsesCreatedChangeWithoutDiff() {
        server.expect(requestTo(BASE + "/api/vacancies/sync"))
                .andRespond(withSuccess("""
                        [{"changeId":7,"changedAt":"2026-11-09T12:30:00Z","vacancyId":"%s","changeType":"created","changes":null}]
                        """.formatted(VACANCY_ID), MediaType.APPLICATION_JSON));

        VacancyChangeDto change = client.sync(List.of(upsert(VACANCY_ID))).getFirst();

        assertThat(change.changeType()).isEqualTo("created");
        assertThat(change.changes()).isNull();
    }

    @Test
    void syncOfEmptyListSendsNothing() {
        assertThat(client.sync(List.of())).isEmpty();
        server.verify();
    }

    @Test
    void syncRejectsMoreThanHundredVacancies() {
        assertThatThrownBy(() -> client.sync(upserts(101))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void syncAllSplitsIntoBatchesOfHundred() {
        server.expect(requestTo(BASE + "/api/vacancies/sync")).andExpect(jsonPath("$.length()").value(100))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/vacancies/sync")).andExpect(jsonPath("$.length()").value(100))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/vacancies/sync")).andExpect(jsonPath("$.length()").value(50))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.syncAll(upserts(250))).isEmpty();

        server.verify();
    }

    @Test
    void syncAllConcatenatesChangesFromAllBatches() {
        String one = """
                [{"changeId":%d,"changedAt":"2026-11-09T12:30:00Z","vacancyId":"v","changeType":"created"}]
                """;
        server.expect(requestTo(BASE + "/api/vacancies/sync"))
                .andRespond(withSuccess(one.formatted(1), MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/vacancies/sync"))
                .andRespond(withSuccess(one.formatted(2), MediaType.APPLICATION_JSON));

        List<VacancyChangeDto> changes = client.syncAll(upserts(150));

        assertThat(changes).extracting(VacancyChangeDto::changeId).containsExactly(1L, 2L);
    }

    @Test
    void getCursorIsEmptyWhenKeyDoesNotExist() {
        server.expect(requestTo(BASE + "/api/sync-state/vacancies.cursor"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"error\":\"not found\"}").contentType(MediaType.APPLICATION_JSON));

        assertThat(client.getCursor()).isEmpty();
        server.verify();
    }

    @Test
    void getCursorParsesStoredValue() {
        server.expect(requestTo(BASE + "/api/sync-state/vacancies.cursor"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"key":"vacancies.cursor","value":"2026-11-09T10:00:00Z","updatedAt":"2026-11-09T10:01:00Z"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getCursor()).contains(Instant.parse("2026-11-09T10:00:00Z"));
    }

    @Test
    void getCursorUnderstandsOffsetNotation() {
        server.expect(requestTo(BASE + "/api/sync-state/vacancies.cursor"))
                .andRespond(withSuccess("""
                        {"key":"vacancies.cursor","value":"2026-11-09T13:00:00+03:00","updatedAt":"2026-11-09T10:01:00Z"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getCursor()).contains(Instant.parse("2026-11-09T10:00:00Z"));
    }

    @Test
    void getCursorFailsOnGarbageValue() {
        server.expect(requestTo(BASE + "/api/sync-state/vacancies.cursor"))
                .andRespond(withSuccess("""
                        {"key":"vacancies.cursor","value":"yesterday","updatedAt":"2026-11-09T10:01:00Z"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getCursor()).isInstanceOf(DbServiceException.class);
    }

    @Test
    void saveCursorPutsIsoValueWithoutFraction() {
        server.expect(requestTo(BASE + "/api/sync-state/vacancies.cursor"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.value").value("2026-11-09T10:00:00Z"))
                .andRespond(withSuccess("""
                        {"key":"vacancies.cursor","value":"2026-11-09T10:00:00Z","updatedAt":"2026-11-09T10:01:00Z"}
                        """, MediaType.APPLICATION_JSON));

        client.saveCursor(Instant.parse("2026-11-09T10:00:00.789Z"));

        server.verify();
    }

    @Test
    void putSyncStateRejectsBlankValue() {
        assertThatThrownBy(() -> client.putSyncState("vacancies.cursor", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getLastModifiedHandlesQuotedValueNullAndBlank() {
        server.expect(requestTo(BASE + "/api/vacancies/last-modified"))
                .andRespond(withSuccess("\"2026-11-09T10:00:00Z\"", MediaType.APPLICATION_JSON));
        assertThat(client.getLastModified()).contains(Instant.parse("2026-11-09T10:00:00Z"));

        server.reset();
        server.expect(requestTo(BASE + "/api/vacancies/last-modified"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
        assertThat(client.getLastModified()).isEmpty();

        server.reset();
        server.expect(requestTo(BASE + "/api/vacancies/last-modified"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));
        assertThat(client.getLastModified()).isEmpty();
    }

    @Test
    void fetchUnpublishedSendsLimitAndParsesList() {
        server.expect(requestTo(startsWith(BASE + "/api/changes/unpublished")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("limit", "200"))
                .andRespond(withSuccess("""
                        [{"changeId":5,"changedAt":"2026-11-09T12:30:00Z","vacancyId":"v1","changeType":"created"},
                         {"changeId":6,"changedAt":"2026-11-09T12:31:00Z","vacancyId":"v2","changeType":"updated",
                          "changes":{"schedule_code":["part","full"]}}]
                        """, MediaType.APPLICATION_JSON));

        List<ru.vacancybot.common.dto.VacancyChangeDto> changes = client.fetchUnpublished(200);

        assertThat(changes).extracting(VacancyChangeDto::changeId).containsExactly(5L, 6L);
        assertThat(changes.get(1).changes().get("schedule_code")).containsExactly("part", "full");
    }

    @Test
    void fetchUnpublishedRejectsLimitOutsideRange() {
        assertThatThrownBy(() -> client.fetchUnpublished(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.fetchUnpublished(1001)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markPublishedSplitsKeysIntoChunksOfThousandAndSumsResult() {
        List<ChangeKeyDto> keys = new ArrayList<>();
        for (long i = 0; i < 1500; i++) {
            keys.add(new ChangeKeyDto(i, Instant.parse("2026-11-09T12:30:00Z")));
        }
        server.expect(requestTo(BASE + "/api/changes/published"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.length()").value(1000))
                .andExpect(jsonPath("$[0].changeId").value(0))
                .andExpect(jsonPath("$[0].changedAt").value("2026-11-09T12:30:00Z"))
                .andRespond(withSuccess("{\"marked\":1000}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/changes/published"))
                .andExpect(jsonPath("$.length()").value(500))
                .andRespond(withSuccess("{\"marked\":500}", MediaType.APPLICATION_JSON));

        assertThat(client.markPublished(keys)).isEqualTo(1500);

        server.verify();
    }

    @Test
    void markPublishedOfEmptyListSendsNothing() {
        assertThat(client.markPublished(List.of())).isZero();
        server.verify();
    }

    @Test
    void markPublishFailedTruncatesLongErrorTo500Characters() {
        server.expect(requestTo(BASE + "/api/changes/publish-failed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.changeId").value(42))
                .andExpect(jsonPath("$.changedAt").value("2026-11-09T12:30:00Z"))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    JsonNode json = new ObjectMapper().readTree(body);
                    assertThat(json.get("error").asText()).hasSize(500);
                })
                .andRespond(withSuccess("{\"updated\":1}", MediaType.APPLICATION_JSON));

        int updated = client.markPublishFailed(
                new ChangeKeyDto(42L, Instant.parse("2026-11-09T12:30:00Z")), "x".repeat(2000));

        assertThat(updated).isEqualTo(1);
        server.verify();
    }

    @Test
    void markPublishFailedToleratesNullError() {
        server.expect(requestTo(BASE + "/api/changes/publish-failed"))
                .andExpect(jsonPath("$.error").value(""))
                .andRespond(withSuccess("{\"updated\":0}", MediaType.APPLICATION_JSON));

        assertThat(client.markPublishFailed(new ChangeKeyDto(1L, Instant.parse("2026-11-09T12:30:00Z")), null)).isZero();
    }

    @Test
    void replaceRegionsPutsWholeDictionary() {
        server.expect(requestTo(BASE + "/api/regions"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("7700000000000"))
                .andRespond(withSuccess());

        client.replaceRegions(List.of(
                new RegionDto("7700000000000", "Москва"),
                new RegionDto("2800000000000", "Амурская область")));

        server.verify();
    }

    @Test
    void replaceRegionsRefusesEmptyListToProtectTheDictionary() {
        assertThatThrownBy(() -> client.replaceRegions(List.of())).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void retriesTemporaryErrorAndSucceeds() {
        server.expect(requestTo(startsWith(BASE + "/api/changes/unpublished")))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(startsWith(BASE + "/api/changes/unpublished")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.fetchUnpublished(10)).isEmpty();

        server.verify();
    }

    @Test
    void givesUpAfterConfiguredNumberOfRetries() {
        server.expect(ExpectedCount.times(3), requestTo(startsWith(BASE + "/api/changes/unpublished")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.fetchUnpublished(10))
                .isInstanceOfSatisfying(DbServiceException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(500);
                    assertThat(e.isRetryable()).isTrue();
                });

        server.verify();
    }

    @Test
    void doesNotRetryClientErrorsAndKeepsServerMessage() {
        server.expect(requestTo(BASE + "/api/vacancies/sync"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"modifiedAt is required\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.sync(List.of(upsert(VACANCY_ID))))
                .isInstanceOfSatisfying(DbServiceException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.isRetryable()).isFalse();
                    assertThat(e.getMessage()).contains("modifiedAt is required");
                });

        server.verify();
    }

    @Test
    void noRetriesWhenDisabled() {
        DbServiceClient noRetries = clientWithRetries(0);
        server.expect(ExpectedCount.once(), requestTo(startsWith(BASE + "/api/changes/unpublished")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> noRetries.fetchUnpublished(10)).isInstanceOf(DbServiceException.class);

        server.verify();
    }

    @Test
    void getSyncStateFailsOnErrorsOtherThan404() {
        server.expect(requestTo(BASE + "/api/sync-state/other"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"bad key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getSyncState("other"))
                .isInstanceOfSatisfying(DbServiceException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(400));
    }
}
