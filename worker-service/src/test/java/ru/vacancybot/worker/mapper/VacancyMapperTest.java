package ru.vacancybot.worker.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.vacancybot.worker.dto.dbservice.VacancyUpsertDto;
import ru.vacancybot.worker.dto.external.TrudvsemVacancyDto;
import ru.vacancybot.worker.dto.external.TrudvsemVacancyDto.TrudvsemCompanyDto;
import ru.vacancybot.worker.dto.external.TrudvsemVacancyDto.TrudvsemRegionDto;
import ru.vacancybot.worker.dto.external.TrudvsemVacancyDto.TrudvsemRequirementDto;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class VacancyMapperTest {

    private static final String ID = "017eb2c8-acb3-11f1-8842-419bde385fdf";

    private final VacancyMapper mapper = new VacancyMapper();

    private static TrudvsemVacancyDto sample() {
        return new TrudvsemVacancyDto(
                ID,
                new TrudvsemRegionDto("2800000000000", "Амурская область"),
                new TrudvsemCompanyDto("ИП КУЛИКОВА И. А."),
                "Грузчик",
                40000,
                45000,
                "Сменная работа",
                "Работа грузчиком",
                new TrudvsemRequirementDto(0),
                "Работа посменно 2/2, 1/3,5/2",
                "https://trudvsem.ru/vacancy/card/324280000045767/" + ID,
                "2026-11-09T03:59:13+0300");
    }

    private static TrudvsemVacancyDto with(String id, String employer, String dateModify) {
        TrudvsemVacancyDto s = sample();
        return new TrudvsemVacancyDto(id, s.region(), new TrudvsemCompanyDto(employer), s.jobName(),
                s.salaryMin(), s.salaryMax(), s.schedule(), s.duty(), s.requirement(),
                s.requirements(), s.vacUrl(), dateModify);
    }

    @Test
    void mapsRealVacancy() {
        VacancyUpsertDto result = mapper.map(sample()).orElseThrow();

        assertThat(result.id()).isEqualTo(ID);
        assertThat(result.employerName()).isEqualTo("ИП КУЛИКОВА И. А.");
        assertThat(result.regionCode()).isEqualTo("2800000000000");
        assertThat(result.regionName()).isEqualTo("Амурская область");
        assertThat(result.jobName()).isEqualTo("Грузчик");
        assertThat(result.salaryMin()).isEqualTo(40000);
        assertThat(result.salaryMax()).isEqualTo(45000);
        assertThat(result.schedule()).isEqualTo("Сменная работа");
        assertThat(result.scheduleCode()).isEqualTo("shift");
        assertThat(result.employmentCode()).isNull();
        assertThat(result.experienceYears()).isZero();
        assertThat(result.modifiedAt()).isEqualTo(Instant.parse("2026-11-09T00:59:13Z"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Вахтовый метод|rotation",
            "Режим гибкого рабочего времени|flex",
            "Сменная работа|shift",
            "Ненормированный рабочий день|irregular",
            "Неполный рабочий день/ неполная рабочая неделя|part",
            "Неполный рабочий день/неполная рабочая неделя|part",
            "Полный рабочий день|full",
            "  полный  РАБОЧИЙ день |full"
    })
    void resolvesScheduleCode(String schedule, String expected) {
        assertThat(mapper.resolveScheduleCode(schedule)).isEqualTo(expected);
    }

    @Test
    void unknownOrMissingScheduleGivesNullCode() {
        assertThat(mapper.resolveScheduleCode("Удалённая работа")).isNull();
        assertThat(mapper.resolveScheduleCode(null)).isNull();
    }

    @Test
    void skipsVacancyWithInvalidId() {
        assertThat(mapper.map(with("not-a-uuid", "ООО Тест", "2026-11-09T03:59:13+0300"))).isEmpty();
    }

    @Test
    void skipsVacancyWithBlankEmployer() {
        assertThat(mapper.map(with(ID, "  ", "2026-11-09T03:59:13+0300"))).isEmpty();
    }

    @Test
    void skipsVacancyWithUnparsableDate() {
        assertThat(mapper.map(with(ID, "ООО Тест", "09.11.2026"))).isEmpty();
    }

    @Test
    void cleansOptionalFields() {
        TrudvsemVacancyDto s = sample();
        TrudvsemVacancyDto source = new TrudvsemVacancyDto(
                ID.toUpperCase(), s.region(), s.company(), " Курьер ", -5, 0, "  ",
                "   ", new TrudvsemRequirementDto(-1), "", s.vacUrl(), s.dateModify());

        VacancyUpsertDto result = mapper.map(source).orElseThrow();

        assertThat(result.id()).isEqualTo(ID);
        assertThat(result.jobName()).isEqualTo("Курьер");
        assertThat(result.salaryMin()).isNull();
        assertThat(result.salaryMax()).isZero();
        assertThat(result.schedule()).isNull();
        assertThat(result.scheduleCode()).isNull();
        assertThat(result.duty()).isNull();
        assertThat(result.requirements()).isNull();
        assertThat(result.experienceYears()).isNull();
    }

    @Test
    void mapAllSkipsInvalidAndCountsThem() {
        VacancyMapper.MappingResult result = mapper.mapAll(Arrays.asList(
                sample(),
                with("not-a-uuid", "ООО Тест", "2026-11-09T03:59:13+0300"),
                null));

        assertThat(result.vacancies()).hasSize(1);
        assertThat(result.skipped()).isEqualTo(2);
    }
}
