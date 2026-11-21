package ru.vacancybot.db.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserStoryIT extends AbstractIntegrationTest {

    private static final long TG = 1001L;
    private static final String VAC = "11111111-1111-1111-1111-111111111111";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    // ---------- помощники ----------

    private static String vacancyJson(String id, int salaryMin, String modifiedAt) {
        return """
            {"id":"%s","employerName":"ООО Ромашка","regionCode":"7700000000000","regionName":"Город Москва",
             "jobName":"Java developer","salaryMin":%d,"salaryMax":150000,"schedule":"Полный рабочий день",
             "scheduleCode":"full","employmentCode":"remote","experienceYears":3,"duty":"Писать код",
             "requirements":"Java","url":"https://trudvsem.ru/vacancy/card/1","modifiedAt":"%s"}
            """.formatted(id, salaryMin, modifiedAt);
    }

    private ResultActions sync(String... vacancies) throws Exception {
        return mvc.perform(post("/api/vacancies/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[" + String.join(",", vacancies) + "]"));
    }

    private JsonNode syncBody(String... vacancies) throws Exception {
        String body = sync(vacancies).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private void putSubscription(String json) throws Exception {
        mvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    // ---------- пользователи и подписки ----------

    @Test
    @DisplayName("пользователь: повторный POST возвращает тот же внутренний id (идемпотентно)")
    void userIsCreatedOnce() throws Exception {
        String first = mvc.perform(post("/api/users/" + TG)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(post("/api/users/" + TG)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("подписка: создать, прочитать, поставить на паузу, удалить")
    void subscriptionLifecycle() throws Exception {
        String created = mvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"telegramUserId":%d,"title":"Java","region":"7700000000000",
                             "salaryMin":80000,"experience":"1_3","schedule":"full","employment":"remote","keywords":"Spring"}
                            """.formatted(TG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$.region", is("7700000000000")))
                .andExpect(jsonPath("$.experience", is("1_3")))
                .andReturn().getResponse().getContentAsString();
        int id = mapper.readTree(created).get("id").asInt();

        mvc.perform(get("/api/users/{tg}/subscriptions", TG))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(patch("/api/users/{tg}/subscriptions/{id}/active?value=false", TG, id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active", is(false)));

        mvc.perform(get("/api/users/{tg}/subscriptions?onlyActive=true", TG))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(delete("/api/users/{tg}/subscriptions/{id}", TG, id))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/{tg}/subscriptions/{id}", TG, id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("подписки другого пользователя недоступны (404)")
    void foreignSubscriptionIsNotVisible() throws Exception {
        String created = mvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telegramUserId\":" + TG + ",\"title\":\"Java\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = mapper.readTree(created).get("id").asInt();
        mvc.perform(post("/api/users/2002")).andExpect(status().isOk());

        mvc.perform(get("/api/users/2002/subscriptions/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("пользователь без подписок получает пустой список, а не 404")
    void unknownUserHasEmptySubscriptionList() throws Exception {
        mvc.perform(get("/api/users/{tg}/subscriptions", 999999))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    // ---------- вакансии: sync, история ----------

    @Test
    @DisplayName("sync: новая вакансия даёт событие created, повтор той же версии не даёт ничего")
    void syncCreatesThenIgnoresSameVersion() throws Exception {
        sync(vacancyJson(VAC, 100000, "2026-11-08T10:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].changeType", is("created")))
                .andExpect(jsonPath("$[0].vacancyId", is(VAC)))
                .andExpect(jsonPath("$[0].changes", nullValue()));

        sync(vacancyJson(VAC, 100000, "2026-11-08T10:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select count(*) from vacancy_changes", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("sync: изменилась зарплата, событие updated с парой [было, стало]")
    void syncDetectsSalaryChange() throws Exception {
        syncBody(vacancyJson(VAC, 100000, "2026-11-08T10:00:00Z"));

        sync(vacancyJson(VAC, 130000, "2026-11-08T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].changeType", is("updated")))
                .andExpect(jsonPath("$[0].changes.salary_min[0]", is(100000)))
                .andExpect(jsonPath("$[0].changes.salary_min[1]", is(130000)));

        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select salary_min from vacancies where id = ?::uuid", Integer.class, VAC))
                .isEqualTo(130000);
    }

    @Test
    @DisplayName("sync: версия новее, но поля те же: события нет, modified_at сдвигается")
    void syncNewerWithoutChangesMovesModifiedAtOnly() throws Exception {
        syncBody(vacancyJson(VAC, 100000, "2026-11-08T10:00:00Z"));

        sync(vacancyJson(VAC, 100000, "2026-11-08T15:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select to_char(modified_at at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS') from vacancies",
                String.class)).isEqualTo("2026-11-08T15:00:00");
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select count(*) from vacancy_changes", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("sync: более старая версия игнорируется")
    void syncIgnoresOlderVersion() throws Exception {
        syncBody(vacancyJson(VAC, 100000, "2026-11-08T10:00:00Z"));

        sync(vacancyJson(VAC, 50000, "2026-11-08T09:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select salary_min from vacancies where id = ?::uuid", Integer.class, VAC))
                .isEqualTo(100000);
    }

    @Test
    @DisplayName("sync: зарплата 0 от API сохраняется как NULL")
    void syncStoresZeroSalaryAsNull() throws Exception {
        syncBody(vacancyJson(VAC, 0, "2026-11-08T10:00:00Z").replace("\"salaryMax\":150000", "\"salaryMax\":0"));

        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select salary_min from vacancies where id = ?::uuid", Integer.class, VAC))
                .isNull();
    }

    @Test
    @DisplayName("sync: битые данные дают 400 с текстом ошибки")
    void syncRejectsBadInput() throws Exception {
        sync(vacancyJson("не-uuid", 1, "2026-11-08T10:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("GET /api/vacancies?ids=: возвращает существующие, неизвестные пропускает")
    void vacanciesByIds() throws Exception {
        syncBody(vacancyJson(VAC, 100000, "2026-11-08T10:00:00Z"));

        mvc.perform(get("/api/vacancies?ids=" + VAC + ",22222222-2222-2222-2222-222222222222"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(VAC)))
                .andExpect(jsonPath("$[0].jobName", is("Java developer")));
    }

    // ---------- outbox ----------

    @Test
    @DisplayName("outbox: события ждут публикации, после «published» пропадают из очереди")
    void outboxFlow() throws Exception {
        JsonNode created = syncBody(vacancyJson(VAC, 100000, "2026-11-08T10:00:00Z")).get(0);
        JsonNode updated = syncBody(vacancyJson(VAC, 130000, "2026-11-08T12:00:00Z")).get(0);

        mvc.perform(get("/api/changes/unpublished"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].changeType", is("created"))) // старые первыми
                .andExpect(jsonPath("$[1].changeType", is("updated")));

        String keys = "[{\"changeId\":%d,\"changedAt\":\"%s\"},{\"changeId\":%d,\"changedAt\":\"%s\"}]".formatted(
                created.get("changeId").asLong(), created.get("changedAt").asText(),
                updated.get("changeId").asLong(), updated.get("changedAt").asText());

        mvc.perform(post("/api/changes/published").contentType(MediaType.APPLICATION_JSON).content(keys))
                .andExpect(status().isOk()).andExpect(jsonPath("$.marked", is(2)));

        mvc.perform(get("/api/changes/unpublished"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));

        // повтор не ломается и ничего не помечает
        mvc.perform(post("/api/changes/published").contentType(MediaType.APPLICATION_JSON).content(keys))
                .andExpect(status().isOk()).andExpect(jsonPath("$.marked", is(0)));
    }

    @Test
    @DisplayName("outbox: после 5 неудачных публикаций событие перестаёт выдаваться")
    void outboxStopsAfterMaxAttempts() throws Exception {
        JsonNode created = syncBody(vacancyJson(VAC, 100000, "2026-11-08T10:00:00Z")).get(0);
        String failed = "{\"changeId\":%d,\"changedAt\":\"%s\",\"error\":\"rabbit недоступен\"}".formatted(
                created.get("changeId").asLong(), created.get("changedAt").asText());

        for (int i = 1; i <= 4; i++) {
            mvc.perform(post("/api/changes/publish-failed").contentType(MediaType.APPLICATION_JSON).content(failed))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.updated", is(1)));
            mvc.perform(get("/api/changes/unpublished")).andExpect(jsonPath("$", hasSize(1)));
        }
        mvc.perform(post("/api/changes/publish-failed").contentType(MediaType.APPLICATION_JSON).content(failed))
                .andExpect(status().isOk());

        mvc.perform(get("/api/changes/unpublished")).andExpect(jsonPath("$", hasSize(0)));
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select last_publish_error from vacancy_changes", String.class))
                .isEqualTo("rabbit недоступен");
    }

    @Test
    @DisplayName("outbox: неизвестное событие в publish-failed даёт updated=0, а не ошибку")
    void publishFailedForUnknownChange() throws Exception {
        mvc.perform(post("/api/changes/publish-failed").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"changeId\":999,\"changedAt\":\"2026-11-08T12:00:00Z\",\"error\":\"x\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated", is(0)));
    }

    // ---------- курсор ----------

    @Test
    @DisplayName("sync-state: сначала 404, потом создаётся и перезаписывается")
    void syncStateCursor() throws Exception {
        mvc.perform(get("/api/sync-state/vacancies.cursor")).andExpect(status().isNotFound());

        mvc.perform(put("/api/sync-state/vacancies.cursor").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"2026-11-08T09:30:00Z\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/sync-state/vacancies.cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key", is("vacancies.cursor")))
                .andExpect(jsonPath("$.value", is("2026-11-08T09:30:00Z")));

        mvc.perform(put("/api/sync-state/vacancies.cursor").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"2026-11-09T09:30:00Z\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/sync-state/vacancies.cursor"))
                .andExpect(jsonPath("$.value", is("2026-11-09T09:30:00Z")));
    }

    @Test
    @DisplayName("sync-state: пустое value даёт 400")
    void syncStateRejectsBlankValue() throws Exception {
        mvc.perform(put("/api/sync-state/vacancies.cursor").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- избранное ----------

    @Test
    @DisplayName("избранное: добавить, повторить без дубля, сменить статус и заметку, удалить")
    void favoritesLifecycle() throws Exception {
        syncBody(vacancyJson(VAC, 100000, "2026-11-08T10:00:00Z"));

        mvc.perform(put("/api/users/{tg}/favorites/{v}", TG, VAC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SAVED")))
                .andExpect(jsonPath("$.vacancyId", is(VAC)))
                .andExpect(jsonPath("$.id", nullValue()));

        mvc.perform(put("/api/users/{tg}/favorites/{v}", TG, VAC)).andExpect(status().isOk());
        mvc.perform(get("/api/users/{tg}/favorites", TG)).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(patch("/api/users/{tg}/favorites/{v}", TG, VAC).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"applied\",\"note\":\"откликнулась\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPLIED")))
                .andExpect(jsonPath("$.note", is("откликнулась")));

        mvc.perform(get("/api/users/{tg}/favorites/{v}", TG, VAC))
                .andExpect(jsonPath("$.status", is("APPLIED")));

        mvc.perform(delete("/api/users/{tg}/favorites/{v}", TG, VAC)).andExpect(status().isNoContent());
        mvc.perform(get("/api/users/{tg}/favorites/{v}", TG, VAC)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/users/{tg}/favorites/{v}", TG, VAC)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("избранное: вакансии нет в базе, 404; неизвестный статус, 400")
    void favoritesValidation() throws Exception {
        mvc.perform(put("/api/users/{tg}/favorites/{v}", TG, "22222222-2222-2222-2222-222222222222"))
                .andExpect(status().isNotFound());

        syncBody(vacancyJson(VAC, 100000, "2026-11-08T10:00:00Z"));
        mvc.perform(put("/api/users/{tg}/favorites/{v}", TG, VAC)).andExpect(status().isOk());
        mvc.perform(patch("/api/users/{tg}/favorites/{v}", TG, VAC).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"WHATEVER\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- регионы ----------

    @Test
    @DisplayName("регионы: справочник из миграции, есть Москва с кодом 7700000000000")
    void regionsComeFromMigration() throws Exception {
        mvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(91)))
                .andExpect(jsonPath("$[?(@.code=='7700000000000')]").exists())
                .andExpect(jsonPath("$[?(@.code=='7800000000000')]").exists());
    }

    // ---------- главный сквозной сценарий ----------

    @Test
    @DisplayName("СКВОЗНОЙ СЦЕНАРИЙ: подписка -> вакансия -> изменение -> outbox -> избранное")
    void fullUserStory() throws Exception {
        // 1. Пользователь создаёт подписку (как делает bot-service)
        putSubscription("{\"telegramUserId\":" + TG + ",\"title\":\"Java\",\"region\":\"7700000000000\"}");

        // 2. Worker присылает новую вакансию, db-service отвечает событием created
        JsonNode created = syncBody(vacancyJson(VAC, 100000, "2026-11-08T10:00:00Z")).get(0);
        org.assertj.core.api.Assertions.assertThat(created.get("changeType").asText()).isEqualTo("created");

        // 3. Worker разгружает outbox: берёт, публикует (здесь условно), помечает
        mvc.perform(get("/api/changes/unpublished")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post("/api/changes/published").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"changeId\":%d,\"changedAt\":\"%s\"}]".formatted(
                                created.get("changeId").asLong(), created.get("changedAt").asText())))
                .andExpect(jsonPath("$.marked", is(1)));

        // 4. Пользователь добавляет вакансию в избранное (кнопка «В избранное»)
        mvc.perform(put("/api/users/{tg}/favorites/{v}", TG, VAC)).andExpect(status().isOk());

        // 5. Работодатель поднял зарплату: приходит обновление
        sync(vacancyJson(VAC, 130000, "2026-11-08T12:00:00Z"))
                .andExpect(jsonPath("$[0].changeType", is("updated")))
                .andExpect(jsonPath("$[0].changes.salary_min[1]", is(130000)));

        // 6. Новое событие снова ждёт публикации
        mvc.perform(get("/api/changes/unpublished"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].changeType", is("updated")));

        // 7. Бот собирает карточки избранного: два запроса (favorites + vacancies?ids=)
        mvc.perform(get("/api/vacancies?ids=" + VAC))
                .andExpect(jsonPath("$[0].salaryMin", is(130000)));

        // 8. Подписка осталась, история записана в БД
        mvc.perform(get("/api/users/{tg}/subscriptions", TG)).andExpect(jsonPath("$", hasSize(1)));
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("select count(*) from vacancy_changes", Integer.class)).isEqualTo(2);
    }
}
