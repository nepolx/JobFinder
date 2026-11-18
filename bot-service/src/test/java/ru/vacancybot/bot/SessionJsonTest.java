package ru.vacancybot.bot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.fsm.DialogState;
import ru.vacancybot.bot.fsm.Session;
import ru.vacancybot.bot.domain.Experience;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Сессия должна переживать JSON (так она хранится в Redis). */
class SessionJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void sessionSurvivesJsonRoundTrip() throws Exception {
        Session s = new Session();
        s.setState(DialogState.CONFIRM_SUBSCRIPTION);
        s.setEditingSubscriptionId(5L);
        s.setPromptMessageId(321);
        s.getDraft().setTitle("Java");
        s.getDraft().setRegionCode("7800000000000");
        s.getDraft().setSalaryMin(80_000);
        s.getDraft().setExperience(Experience.Y1_3);
        s.getDraft().setScheduleCode("full");
        s.getDraft().setKeywords("Spring, SQL");

        String json = mapper.writeValueAsString(s);
        Session back = mapper.readValue(json, Session.class);

        assertEquals(DialogState.CONFIRM_SUBSCRIPTION, back.getState());
        assertEquals(5L, back.getEditingSubscriptionId());
        assertEquals(321, back.getPromptMessageId());
        assertEquals(s.getDraft().toCriteria(), back.getDraft().toCriteria());
        assertFalse(back.getDraft().isEmpty());
    }

    @Test
    void freshSessionRoundTrips() throws Exception {
        Session back = mapper.readValue(mapper.writeValueAsString(new Session()), Session.class);
        assertEquals(DialogState.IDLE, back.getState());
        assertEquals(true, back.getDraft().isEmpty());
    }
}
