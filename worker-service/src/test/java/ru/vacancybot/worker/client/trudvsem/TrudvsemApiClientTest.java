package ru.vacancybot.worker.client.trudvsem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.vacancybot.worker.client.trudvsem.TrudvsemApiClient.VacancyPage;
import ru.vacancybot.worker.exception.TrudvsemApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TrudvsemApiClientTest {

    private static final String BASE = "http://trudvsem.test";
    private static final String PATH = BASE + "/api/v1/vacancies";
    private static final Instant FROM = Instant.parse("2026-11-09T00:59:09Z");
    private static final Instant TO = Instant.parse("2026-11-09T00:59:13Z");

    private static final String TWO_VACANCIES = """
            {
              "status": "200",
              "request": {"api": "v1"},
              "meta": {"total": 2, "limit": 100},
              "results": {"vacancies": [
                {"vacancy": {
                  "id": "017eb2c8-acb3-11f1-8842-419bde385fdf",
                  "region": {"region_code": "2800000000000", "name": "Амурская область"},
                  "company": {"name": "ИП КУЛИКОВА И. А.", "inn": "280107858454"},
                  "job-name": "Грузчик",
                  "salary_min": 40000,
                  "salary_max": 45000,
                  "schedule": "Сменная работа",
                  "duty": "Работа грузчиком",
                  "requirement": {"education": "Tребования не предъявляются", "experience": 0},
                  "requirements": "Работа посменно 2/2",
                  "vac_url": "https://trudvsem.ru/vacancy/card/324280000045767/017eb2c8",
                  "date_modify": "2026-11-09T03:59:13+0300",
                  "unknown_field": "must be ignored"
                }},
                {"vacancy": {
                  "id": "e5367948-500f-11f1-a502-f17e9abd8b94",
                  "region": {"region_code": "2800000000000", "name": "Амурская область"},
                  "company": {"name": "ИП КУЛИКОВА И. А."},
                  "job-name": "Продавец-кассир",
                  "salary_min": 40000,
                  "schedule": "Полный рабочий день",
                  "requirement": {"experience": 1},
                  "vac_url": "https://trudvsem.ru/vacancy/card/324280000045767/e5367948",
                  "date_modify": "2026-11-09T03:59:09+0300"
                }}
              ]}
            }
            """;

    private MockRestServiceServer server;
    private TrudvsemApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        TrudvsemApiProperties properties =
                new TrudvsemApiProperties(BASE, 100, Duration.ofSeconds(1), Duration.ofSeconds(1));
        client = new TrudvsemApiClient(builder.build(), properties);
    }

    @Test
    void parsesPageAndSendsWindowParameters() {
        server.expect(requestTo(startsWith(PATH)))
                .andExpect(queryParam("offset", "0"))
                .andExpect(queryParam("limit", "100"))
                .andExpect(queryParam("modifiedFrom", "2026-11-09T00:59:09Z"))
                .andExpect(queryParam("modifiedTo", "2026-11-09T00:59:13Z"))
                .andRespond(withSuccess(TWO_VACANCIES, MediaType.APPLICATION_JSON));

        VacancyPage page = client.fetchPage(0, FROM, TO);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.page()).isZero();
        assertThat(page.vacancies()).hasSize(2);
        assertThat(page.vacancies().getFirst().id()).isEqualTo("017eb2c8-acb3-11f1-8842-419bde385fdf");
        assertThat(page.vacancies().getFirst().jobName()).isEqualTo("Грузчик");
        assertThat(page.vacancies().getFirst().company().name()).isEqualTo("ИП КУЛИКОВА И. А.");
        assertThat(page.vacancies().getFirst().region().regionCode()).isEqualTo("2800000000000");
        assertThat(page.vacancies().get(0).requirement().experience()).isZero();
        assertThat(page.vacancies().get(0).dateModify()).isEqualTo("2026-11-09T03:59:13+0300");
        assertThat(page.vacancies().get(1).salaryMax()).isNull();
        server.verify();
    }

    @Test
    void usesPageSizeFromPropertiesWhenLimitIsNotGiven() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer local = MockRestServiceServer.bindTo(builder).build();
        TrudvsemApiClient smallPages = new TrudvsemApiClient(builder.build(),
                new TrudvsemApiProperties(BASE, 25, Duration.ofSeconds(1), Duration.ofSeconds(1)));
        local.expect(requestTo(startsWith(PATH))).andExpect(queryParam("limit", "25"))
                .andRespond(withSuccess(TWO_VACANCIES, MediaType.APPLICATION_JSON));

        VacancyPage page = smallPages.fetchPage(3, FROM, TO);

        assertThat(page.limit()).isEqualTo(25);
        assertThat(page.page()).isEqualTo(3);
        local.verify();
    }

    @Test
    void omitsModifiedToWhenNull() {
        server.expect(requestTo(not(containsString("modifiedTo"))))
                .andExpect(queryParam("modifiedFrom", "2026-11-09T00:59:09Z"))
                .andRespond(withSuccess(TWO_VACANCIES, MediaType.APPLICATION_JSON));

        client.fetchPage(0, FROM, null);

        server.verify();
    }

    @Test
    void dropsFractionOfSecondFromBounds() {
        server.expect(requestTo(startsWith(PATH)))
                .andExpect(queryParam("modifiedFrom", "2026-11-09T00:59:09Z"))
                .andExpect(queryParam("modifiedTo", "2026-11-09T00:59:13Z"))
                .andRespond(withSuccess(TWO_VACANCIES, MediaType.APPLICATION_JSON));

        client.fetchPage(0, Instant.parse("2026-11-09T00:59:09.987Z"), Instant.parse("2026-11-09T00:59:13.500Z"));

        server.verify();
    }

    @Test
    void emptyResultsGiveEmptyPage() {
        server.expect(requestTo(startsWith(PATH)))
                .andRespond(withSuccess("""
                        {"status":"200","request":{"api":"v1"},"meta":{"total":0,"limit":100},"results":{}}
                        """, MediaType.APPLICATION_JSON));

        VacancyPage page = client.fetchPage(0, FROM, TO);

        assertThat(page.isEmpty()).isTrue();
        assertThat(page.total()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void skipsNullElementsInVacancyList() {
        server.expect(requestTo(startsWith(PATH)))
                .andRespond(withSuccess("""
                        {"status":"200","meta":{"total":2,"limit":100},
                         "results":{"vacancies":[null,{"vacancy":null},{"vacancy":{"id":"a"}}]}}
                        """, MediaType.APPLICATION_JSON));

        VacancyPage page = client.fetchPage(0, FROM, TO);

        assertThat(page.vacancies()).hasSize(1);
        assertThat(page.vacancies().getFirst().id()).isEqualTo("a");
    }

    @Test
    void bodyStatusNot200BecomesRetryableErrorWithApiMessage() {
        server.expect(requestTo(startsWith(PATH)))
                .andRespond(withSuccess("""
                        {"status":"500","request":{"api":"v1"},"meta":{"error":"Проверьте синтаксис запроса"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchPage(0, FROM, TO))
                .isInstanceOfSatisfying(TrudvsemApiException.class, e -> {
                    assertThat(e.getMessage()).contains("500").contains("Проверьте синтаксис запроса");
                    assertThat(e.isRetryable()).isTrue();
                    assertThat(e.getHttpStatus()).isNull();
                });
    }

    @Test
    void httpServerErrorIsRetryableAndKeepsBodySnippet() {
        server.expect(requestTo(startsWith(PATH)))
                .andRespond(withServerError().body("{\"error\":\"boom\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchPage(0, FROM, TO))
                .isInstanceOfSatisfying(TrudvsemApiException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(500);
                    assertThat(e.isRetryable()).isTrue();
                    assertThat(e.getMessage()).contains("boom");
                });
    }

    @Test
    void tooManyRequestsIsRetryable() {
        server.expect(requestTo(startsWith(PATH))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.fetchPage(0, FROM, TO))
                .isInstanceOfSatisfying(TrudvsemApiException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(429);
                    assertThat(e.isRetryable()).isTrue();
                });
    }

    @Test
    void clientErrorIsNotRetryable() {
        server.expect(requestTo(startsWith(PATH))).andRespond(withBadRequest());

        assertThatThrownBy(() -> client.fetchPage(0, FROM, TO))
                .isInstanceOfSatisfying(TrudvsemApiException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(400);
                    assertThat(e.isRetryable()).isFalse();
                });
    }

    @Test
    void missingTotalIsAnError() {
        server.expect(requestTo(startsWith(PATH)))
                .andRespond(withSuccess("""
                        {"status":"200","meta":{"limit":100},"results":{}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchPage(0, FROM, TO))
                .isInstanceOf(TrudvsemApiException.class)
                .hasMessageContaining("meta.total");
    }

    @Test
    void unreadableBodyIsNotRetryable() {
        server.expect(requestTo(startsWith(PATH)))
                .andRespond(withSuccess("<html>maintenance</html>", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchPage(0, FROM, TO))
                .isInstanceOfSatisfying(TrudvsemApiException.class, e -> assertThat(e.isRetryable()).isFalse());
    }

    @ParameterizedTest
    @CsvSource({"-1", "100", "250"})
    void rejectsPageOutsideReadableRange(int page) {
        assertThatThrownBy(() -> client.fetchPage(page, FROM, TO)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"0", "101", "-5"})
    void rejectsLimitOutsideRange(int limit) {
        assertThatThrownBy(() -> client.fetchPage(0, limit, FROM, TO)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsReversedWindowAndMissingFrom() {
        assertThatThrownBy(() -> client.fetchPage(0, TO, FROM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.fetchPage(0, null, TO)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void acceptsLastReadablePage() {
        server.expect(requestTo(startsWith(PATH))).andExpect(queryParam("offset", "99"))
                .andRespond(withSuccess(TWO_VACANCIES, MediaType.APPLICATION_JSON));

        assertThat(client.fetchPage(99, FROM, TO).page()).isEqualTo(99);
    }

    @Test
    void findLatestModifiedReadsNewestRecord() {
        server.expect(requestTo(startsWith(PATH)))
                .andExpect(queryParam("offset", "0"))
                .andExpect(queryParam("limit", "1"))
                .andRespond(withSuccess(TWO_VACANCIES, MediaType.APPLICATION_JSON));

        Optional<Instant> latest = client.findLatestModified(FROM);

        assertThat(latest).contains(Instant.parse("2026-11-09T00:59:13Z"));
    }

    @Test
    void findLatestModifiedIsEmptyWhenNothingFound() {
        server.expect(requestTo(startsWith(PATH)))
                .andRespond(withSuccess("""
                        {"status":"200","meta":{"total":0,"limit":1},"results":{}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findLatestModified(FROM)).isEmpty();
    }

    @Test
    void findLatestModifiedFailsOnUnparsableDate() {
        server.expect(requestTo(startsWith(PATH)))
                .andRespond(withSuccess("""
                        {"status":"200","meta":{"total":1,"limit":1},
                         "results":{"vacancies":[{"vacancy":{"id":"a","date_modify":"yesterday"}}]}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.findLatestModified(FROM))
                .isInstanceOfSatisfying(TrudvsemApiException.class, e -> assertThat(e.isRetryable()).isFalse());
    }

    @Test
    void pageCountsPagesAndDetectsNextPage() {
        assertThat(new VacancyPage(List.of(), 250, 0, 100).totalPages()).isEqualTo(3);
        assertThat(new VacancyPage(List.of(), 250, 0, 100).hasNext()).isTrue();
        assertThat(new VacancyPage(List.of(), 250, 1, 100).hasNext()).isTrue();
        assertThat(new VacancyPage(List.of(), 250, 2, 100).hasNext()).isFalse();
        assertThat(new VacancyPage(List.of(), 200, 1, 100).hasNext()).isFalse();
        assertThat(new VacancyPage(List.of(), 100, 0, 100).hasNext()).isFalse();
        assertThat(new VacancyPage(List.of(), 0, 0, 100).hasNext()).isFalse();
        assertThat(new VacancyPage(List.of(), 0, 0, 100).totalPages()).isZero();
    }

    @Test
    void pageNeverOffersPagesBeyondApiLimit() {
        assertThat(new VacancyPage(List.of(), 50_000, 98, 100).hasNext()).isTrue();
        assertThat(new VacancyPage(List.of(), 50_000, 99, 100).hasNext()).isFalse();
    }

    @Test
    void pageDetectsWindowThatDoesNotFitInOneQuery() {
        assertThat(new VacancyPage(List.of(), 10_000, 0, 100).exceedsReadableRange()).isFalse();
        assertThat(new VacancyPage(List.of(), 10_001, 0, 100).exceedsReadableRange()).isTrue();
        assertThat(new VacancyPage(List.of(), 2_500, 0, 25).exceedsReadableRange()).isFalse();
        assertThat(new VacancyPage(List.of(), 2_501, 0, 25).exceedsReadableRange()).isTrue();
    }
}
