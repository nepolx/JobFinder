package ru.vacancybot.bot;

import org.junit.jupiter.api.Test;
import ru.vacancybot.bot.fsm.InputValidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputValidatorTest {

    @Test
    void title_trimsAndChecksLength() {
        assertEquals("Java разработчик", InputValidator.title("  Java разработчик ").value());
        assertTrue(InputValidator.title("x".repeat(100)).ok());
        assertFalse(InputValidator.title("x".repeat(101)).ok());
        assertFalse(InputValidator.title("   ").ok());
        assertFalse(InputValidator.title(null).ok());
    }

    @Test
    void salary_acceptsNonNegativeIntegers() {
        assertEquals(80_000, InputValidator.salary("80000").value());
        assertEquals(80_000, InputValidator.salary(" 80 000 ").value());
        assertEquals(80_000, InputValidator.salary("80\u00A0000").value());
    }

    @Test
    void salary_zeroMeansSkip() {
        var r = InputValidator.salary("0");
        assertTrue(r.ok());
        assertNull(r.value());
    }

    @Test
    void salary_rejectsGarbage() {
        for (String bad : new String[]{"abc", "-5", "80.5", "80000 руб", "", "   ", "1234567890", null}) {
            var r = InputValidator.salary(bad);
            assertFalse(r.ok(), "must be rejected: " + bad);
            assertEquals(InputValidator.SALARY_HINT, r.error());
        }
    }

    @Test
    void keywords_areNormalized() {
        assertEquals("Spring, SQL, Kafka", InputValidator.keywords(" Spring,SQL ,, Kafka ,").value());
    }

    @Test
    void keywords_limitedTo200Characters() {
        assertTrue(InputValidator.keywords("a".repeat(200)).ok());
        assertFalse(InputValidator.keywords("a".repeat(201)).ok());
        assertFalse(InputValidator.keywords(" , , ").ok());
        assertFalse(InputValidator.keywords(null).ok());
    }
}
