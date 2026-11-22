package ru.vacancybot.bot.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.domain.Favorite;
import ru.vacancybot.bot.domain.FavoriteStatus;
import ru.vacancybot.bot.domain.Subscription;
import ru.vacancybot.bot.domain.SubscriptionCriteria;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Клиенты к db-service против фейкового HTTP-сервера: проверяем пути, методы, тела и разбор ответов. */
class DbClientsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final long TG = 42;
    private static final String V1 = "11111111-1111-1111-1111-111111111111";
    private static final String V2 = "22222222-2222-2222-2222-222222222222";

    private FakeDbServer server;
    private DbServiceClient db;
    private HttpSubscriptionService subs;
    private HttpFavoriteService favs;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeDbServer();
        db = new DbServiceClient(server.baseUrl() + "/", MAPPER);
        subs = new HttpSubscriptionService(db);
        favs = new HttpFavoriteService(db);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private static String subJson(long id, boolean active) {
        return "{\"id\":" + id + ",\"telegramUserId\":42,\"title\":\"Java\",\"region\":\"7800000000000\","
                + "\"salaryMin\":80000,\"experience\":\"1_3\",\"schedule\":\"full\",\"employment\":null,"
                + "\"keywords\":\"Spring\",\"active\":" + active + "}";
    }

    private static String vacJson(String id, String name) {
        return "{\"id\":\"" + id + "\",\"jobName\":\"" + name + "\",\"employerName\":\"ООО Ромашка\","
                + "\"regionName\":\"Москва\",\"salaryMin\":100000,\"salaryMax\":null,\"schedule\":\"Полный день\","
                + "\"url\":\"https://trudvsem.ru/vacancy/card/" + id + "\",\"extra\":\"ignored\"}";
    }

    // ---------- пользователи ----------

    @Test
    void register_postsToUsersEndpoint_acceptsNoContent() {
        server.handler = r -> FakeDbServer.Resp.status(204);
        new HttpUserService(db).register(TG);
        assertEquals("POST", server.last().method());
        assertEquals("/api/users/42", server.last().target());
    }

    // ---------- подписки ----------

    @Test
    void create_sendsDtoAndMapsResponse() throws Exception {
        server.handler = r -> new FakeDbServer.Resp(201, subJson(5, true));
        var criteria = new SubscriptionCriteria("Java", "7800000000000", 80_000, "1_3", "full", null, "Spring");

        Subscription created = subs.create(TG, criteria);

        assertEquals("POST", server.last().method());
        assertEquals("/api/subscriptions", server.last().target());
        JsonNode sent = MAPPER.readTree(server.last().body());
        assertEquals(42, sent.get("telegramUserId").asInt());
        assertEquals("Java", sent.get("title").asText());
        assertEquals("7800000000000", sent.get("region").asText());
        assertEquals("1_3", sent.get("experience").asText());
        assertEquals("full", sent.get("schedule").asText());
        assertTrue(sent.get("employment").isNull());
        assertTrue(sent.get("active").asBoolean());
        assertEquals(5, created.id());
        assertEquals(criteria, created.criteria());
        assertTrue(created.active());
    }

    @Test
    void list_mapsEverySubscription_zeroSalaryAndBlanksBecomeNull() {
        server.handler = r -> FakeDbServer.Resp.ok("[" + subJson(1, true) + ","
                + "{\"id\":2,\"telegramUserId\":42,\"title\":\"\",\"region\":null,\"salaryMin\":0,\"experience\":null,"
                + "\"schedule\":null,\"employment\":\"remote\",\"keywords\":\"  \",\"active\":false}]");

        List<Subscription> list = subs.list(TG);

        assertEquals("GET", server.last().method());
        assertEquals("/api/users/42/subscriptions", server.last().target());
        assertEquals(2, list.size());
        assertEquals(new SubscriptionCriteria(null, null, null, null, null, "remote", null), list.get(1).criteria());
        assertFalse(list.get(1).active());
    }

    @Test
    void update_putsToSubscriptionPath_404MeansNoSuchElement() {
        server.handler = r -> FakeDbServer.Resp.ok(subJson(5, false));
        var criteria = new SubscriptionCriteria("Java", null, null, null, null, null, null);

        Subscription updated = subs.update(TG, 5, criteria);

        assertEquals("PUT", server.last().method());
        assertEquals("/api/users/42/subscriptions/5", server.last().target());
        assertFalse(updated.active());

        server.handler = r -> FakeDbServer.Resp.status(404);
        assertThrows(NoSuchElementException.class, () -> subs.update(TG, 6, criteria));
    }

    @Test
    void setActive_patchesWithQueryParameter() {
        server.handler = r -> FakeDbServer.Resp.ok(subJson(5, false));
        subs.setActive(TG, 5, false);
        assertEquals("PATCH", server.last().method());
        assertEquals("/api/users/42/subscriptions/5/active?value=false", server.last().target());
        subs.setActive(TG, 5, true);
        assertEquals("/api/users/42/subscriptions/5/active?value=true", server.last().target());
    }

    @Test
    void delete_sendsDelete() {
        server.handler = r -> FakeDbServer.Resp.status(204);
        subs.delete(TG, 5);
        assertEquals("DELETE", server.last().method());
        assertEquals("/api/users/42/subscriptions/5", server.last().target());
    }

    @Test
    void find_returnsSubscription_or_emptyOn404() {
        server.handler = r -> FakeDbServer.Resp.ok(subJson(5, true));
        assertEquals(5, subs.find(TG, 5).orElseThrow().id());
        assertEquals("/api/users/42/subscriptions/5", server.last().target());

        server.handler = r -> FakeDbServer.Resp.status(404);
        assertTrue(subs.find(TG, 5).isEmpty());
    }

    @Test
    void find_fallsBackToListWhenDbServiceHasNoSingleGet() {
        server.handler = r -> r.method().equals("GET") && r.target().endsWith("/subscriptions")
                ? FakeDbServer.Resp.ok("[" + subJson(5, true) + "]")
                : FakeDbServer.Resp.status(405);

        assertEquals(5, subs.find(TG, 5).orElseThrow().id());
        assertTrue(subs.find(TG, 99).isEmpty());
    }

    @Test
    void serverError_becomesDbServiceExceptionWithStatus() {
        server.handler = r -> new FakeDbServer.Resp(500, "{\"error\":\"boom\"}");
        var e = assertThrows(DbServiceException.class, () -> subs.list(TG));
        assertEquals(500, e.status());
        assertTrue(e.getMessage().contains("boom"));
    }

    @Test
    void unreachableDbService_becomesDbServiceException() {
        server.close();
        var e = assertThrows(DbServiceException.class, () -> subs.list(TG));
        assertEquals(0, e.status());
    }

    // ---------- избранное ----------

    @Test
    void favoriteAdd_putsWithoutBody_404MeansVacancyNotFound() {
        server.handler = r -> FakeDbServer.Resp.ok("{\"id\":null,\"telegramUserId\":42,\"vacancyId\":\"" + V1 + "\",\"status\":\"SAVED\",\"note\":null}");
        favs.add(TG, V1);
        assertEquals("PUT", server.last().method());
        assertEquals("/api/users/42/favorites/" + V1, server.last().target());
        assertEquals("", server.last().body());

        server.handler = r -> FakeDbServer.Resp.status(404);
        assertThrows(NoSuchElementException.class, () -> favs.add(TG, V2));
    }

    @Test
    void favoriteRemove_sendsDelete() {
        server.handler = r -> FakeDbServer.Resp.status(204);
        favs.remove(TG, V1);
        assertEquals("DELETE", server.last().method());
        assertEquals("/api/users/42/favorites/" + V1, server.last().target());
    }

    @Test
    void favoriteSetStatus_patchesStatusNameAndLeavesNoteNull() throws Exception {
        server.handler = r -> FakeDbServer.Resp.ok("{}");
        favs.setStatus(TG, V1, FavoriteStatus.APPLIED);
        assertEquals("PATCH", server.last().method());
        assertEquals("/api/users/42/favorites/" + V1, server.last().target());
        JsonNode sent = MAPPER.readTree(server.last().body());
        assertEquals("APPLIED", sent.get("status").asText());
        assertTrue(sent.get("note").isNull());
    }

    @Test
    void favoriteList_joinsFavoritesWithVacanciesInOneExtraRequest() {
        server.handler = r -> {
            if (r.target().equals("/api/users/42/favorites")) {
                return FakeDbServer.Resp.ok("[{\"id\":null,\"telegramUserId\":42,\"vacancyId\":\"" + V1 + "\",\"status\":\"WAITING\",\"note\":null},"
                        + "{\"id\":null,\"telegramUserId\":42,\"vacancyId\":\"" + V2 + "\",\"status\":\"SAVED\",\"note\":null}]");
            }
            if (r.target().equals("/api/vacancies?ids=" + V1 + "," + V2)) {
                // порядок в ответе другой — склейка идёт по id
                return FakeDbServer.Resp.ok("[" + vacJson(V2, "Второй") + "," + vacJson(V1, "Первый") + "]");
            }
            return FakeDbServer.Resp.status(500);
        };

        List<Favorite> list = favs.list(TG);

        assertEquals(2, server.requests.size());
        assertEquals(2, list.size());
        assertEquals("Первый", list.get(0).vacancy().jobName());
        assertEquals(FavoriteStatus.WAITING, list.get(0).status());
        assertEquals("Второй", list.get(1).vacancy().jobName());
        assertEquals(100_000, list.get(1).vacancy().salaryMin());
        assertNull(list.get(1).vacancy().salaryMax());
    }

    @Test
    void favoriteList_skipsFavoritesWhoseVacancyIsMissing_andHandlesEmpty() {
        server.handler = r -> r.target().startsWith("/api/vacancies")
                ? FakeDbServer.Resp.ok("[" + vacJson(V1, "Есть") + "]")
                : FakeDbServer.Resp.ok("[{\"vacancyId\":\"" + V1 + "\",\"status\":\"UNKNOWN_STATUS\"},{\"vacancyId\":\"" + V2 + "\",\"status\":\"SAVED\"}]");

        List<Favorite> list = favs.list(TG);

        assertEquals(1, list.size());
        assertEquals(FavoriteStatus.SAVED, list.get(0).status(), "неизвестный статус не должен ронять бота");

        server.requests.clear();
        server.handler = r -> FakeDbServer.Resp.ok("[]");
        assertTrue(favs.list(TG).isEmpty());
        assertEquals(1, server.requests.size(), "без избранного запрос за вакансиями не нужен");
    }

    @Test
    void favoriteFind_combinesDtoAndVacancy_emptyOn404() {
        server.handler = r -> r.target().startsWith("/api/vacancies")
                ? FakeDbServer.Resp.ok("[" + vacJson(V1, "Первый") + "]")
                : FakeDbServer.Resp.ok("{\"vacancyId\":\"" + V1 + "\",\"status\":\"APPLIED\",\"note\":\"n\"}");

        Favorite f = favs.find(TG, V1).orElseThrow();
        assertEquals(FavoriteStatus.APPLIED, f.status());
        assertEquals("Первый", f.vacancy().jobName());

        server.handler = r -> FakeDbServer.Resp.status(404);
        assertTrue(favs.find(TG, V2).isEmpty());
    }

    @Test
    void vacancyIdIsPercentEncodedInPath() {
        server.handler = r -> FakeDbServer.Resp.status(204);
        favs.remove(TG, "a b/c");
        assertEquals("/api/users/42/favorites/a%20b%2Fc", server.last().target());
    }
}
