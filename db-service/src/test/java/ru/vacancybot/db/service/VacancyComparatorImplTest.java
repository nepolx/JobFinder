package ru.vacancybot.db.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.vacancybot.db.entity.Vacancy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VacancyComparatorImplTest {

    private final VacancyComparatorImpl comparator = new VacancyComparatorImpl();

    private static Vacancy vacancy() {
        Vacancy v = new Vacancy(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        v.setEmployerName("ООО Ромашка");
        v.setRegionCode("7700000000000");
        v.setRegionName("Город Москва");
        v.setJobName("Java developer");
        v.setSalaryMin(100_000);
        v.setSalaryMax(150_000);
        v.setSchedule("Полный рабочий день");
        v.setScheduleCode("full");
        v.setEmploymentCode("remote");
        v.setExperienceYears(3);
        v.setDuty("Писать код");
        v.setRequirements("Java, Spring");
        v.setVacUrl("https://trudvsem.ru/vacancy/card/1");
        v.setModifiedAt(Instant.parse("2026-11-08T10:00:00Z"));
        return v;
    }

    @Test
    @DisplayName("одинаковые вакансии: различий нет")
    void sameVacancies_emptyMap() {
        assertThat(comparator.diff(vacancy(), vacancy())).isEmpty();
    }

    @Test
    @DisplayName("изменилась зарплата: ключ salary_min, значения [было, стало]")
    void salaryMinChanged() {
        Vacancy incoming = vacancy();
        incoming.setSalaryMin(130_000);

        Map<String, List<Object>> diff = comparator.diff(vacancy(), incoming);

        assertThat(diff).containsOnlyKeys("salary_min");
        assertThat(diff.get("salary_min")).containsExactly(100_000, 130_000);
    }

    @Test
    @DisplayName("null -> значение считается изменением")
    void nullToValue() {
        Vacancy old = vacancy();
        old.setSalaryMax(null);

        Map<String, List<Object>> diff = comparator.diff(old, vacancy());

        assertThat(diff).containsOnlyKeys("salary_max");
        assertThat(diff.get("salary_max")).containsExactly(null, 150_000);
    }

    @Test
    @DisplayName("значение -> null тоже изменение")
    void valueToNull() {
        Vacancy incoming = vacancy();
        incoming.setDuty(null);

        Map<String, List<Object>> diff = comparator.diff(vacancy(), incoming);

        assertThat(diff).containsOnlyKeys("duty");
        assertThat(diff.get("duty")).containsExactly("Писать код", null);
    }

    @Test
    @DisplayName("оба null: изменения нет")
    void bothNull_noChange() {
        Vacancy a = vacancy();
        Vacancy b = vacancy();
        a.setRequirements(null);
        b.setRequirements(null);

        assertThat(comparator.diff(a, b)).isEmpty();
    }

    @Test
    @DisplayName("изменилось несколько полей: все попали в карту")
    void severalFieldsChanged() {
        Vacancy incoming = vacancy();
        incoming.setJobName("Senior Java developer");
        incoming.setEmployerName("ООО Лютик");
        incoming.setSchedule("Сменная работа");
        incoming.setScheduleCode("shift");
        incoming.setEmploymentCode("temp");
        incoming.setExperienceYears(5);
        incoming.setRequirements("Java, Spring, Kafka");

        Map<String, List<Object>> diff = comparator.diff(vacancy(), incoming);

        assertThat(diff).containsOnlyKeys("job_name", "employer_name", "schedule",
                "schedule_code", "employment_code", "experience_years", "requirements");
        assertThat(diff.get("job_name")).containsExactly("Java developer", "Senior Java developer");
        assertThat(diff.get("experience_years")).containsExactly(3, 5);
    }

    @Test
    @DisplayName("поля, которые не отслеживаем (регион, ссылка, modified_at), различий не дают")
    void untrackedFieldsIgnored() {
        Vacancy incoming = vacancy();
        incoming.setRegionCode("7800000000000");
        incoming.setRegionName("Санкт-Петербург");
        incoming.setVacUrl("https://trudvsem.ru/vacancy/card/999");
        incoming.setModifiedAt(Instant.parse("2026-11-09T10:00:00Z"));

        assertThat(comparator.diff(vacancy(), incoming)).isEmpty();
    }

    @Test
    @DisplayName("порядок ключей стабильный (LinkedHashMap): как в описании полей")
    void keysKeepDeclaredOrder() {
        Vacancy incoming = vacancy();
        incoming.setRequirements("другое");
        incoming.setJobName("другое");
        incoming.setSalaryMin(1);

        assertThat(comparator.diff(vacancy(), incoming).keySet())
                .containsExactly("job_name", "salary_min", "requirements");
    }
}
