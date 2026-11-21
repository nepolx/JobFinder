package ru.vacancybot.db.mapper;

import org.junit.jupiter.api.Test;
import ru.vacancybot.common.dto.VacancyUpsertDto;
import ru.vacancybot.db.entity.Vacancy;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VacancyUpsertMapperTest {

    private static final String ID = "11111111-1111-1111-1111-111111111111";
    private static final Instant T = Instant.parse("2026-11-08T10:00:00Z");

    private static VacancyUpsertDto dto(String id, Integer min, Integer max, String duty, String jobName) {
        return new VacancyUpsertDto(id, "ООО Ромашка", "7700000000000", "Город Москва", jobName,
                min, max, "Полный рабочий день", "full", "remote", 3, duty, "Java",
                "https://trudvsem.ru/vacancy/card/1", T);
    }

    @Test
    void zeroSalaryBecomesNull() {
        Vacancy v = VacancyUpsertMapper.toEntity(dto(ID, 0, 0, "x", "Java"));

        assertThat(v.getSalaryMin()).isNull();
        assertThat(v.getSalaryMax()).isNull();
    }

    @Test
    void positiveSalaryIsKept() {
        Vacancy v = VacancyUpsertMapper.toEntity(dto(ID, 80_000, 120_000, "x", "Java"));

        assertThat(v.getSalaryMin()).isEqualTo(80_000);
        assertThat(v.getSalaryMax()).isEqualTo(120_000);
    }

    @Test
    void blankTextBecomesNull_andTextIsTrimmed() {
        Vacancy v = VacancyUpsertMapper.toEntity(dto(ID, 1, 2, "   ", "  Java  "));

        assertThat(v.getDuty()).isNull();
        assertThat(v.getJobName()).isEqualTo("Java");
    }

    @Test
    void idMustBeUuid() {
        assertThatThrownBy(() -> VacancyUpsertMapper.toEntity(dto("не-uuid", 1, 2, "x", "Java")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    void requiredFieldsAreChecked() {
        assertThatThrownBy(() -> VacancyUpsertMapper.toEntity(dto(ID, 1, 2, "x", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jobName");

        VacancyUpsertDto noDate = new VacancyUpsertDto(ID, "e", "7700000000000", "Москва", "Java",
                1, 2, null, null, null, null, null, null, "https://x", null);
        assertThatThrownBy(() -> VacancyUpsertMapper.toEntity(noDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modifiedAt");
    }

    @Test
    void applyTo_overwritesExistingEntity_withoutChangingId() {
        Vacancy existing = VacancyUpsertMapper.toEntity(dto(ID, 1, 2, "старое", "Java"));

        VacancyUpsertMapper.applyTo(dto(ID, 5, 6, "новое", "Kotlin"), existing);

        assertThat(existing.getId().toString()).isEqualTo(ID);
        assertThat(existing.getJobName()).isEqualTo("Kotlin");
        assertThat(existing.getSalaryMin()).isEqualTo(5);
        assertThat(existing.getDuty()).isEqualTo("новое");
    }
}
