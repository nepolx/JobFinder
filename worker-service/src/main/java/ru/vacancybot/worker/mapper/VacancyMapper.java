package ru.vacancybot.worker.mapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.vacancybot.worker.client.trudvsem.TrudvsemDateParser;
import ru.vacancybot.worker.dto.dbservice.VacancyUpsertDto;
import ru.vacancybot.worker.dto.external.TrudvsemVacancyDto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Component
public class VacancyMapper {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final Set<String> unknownSchedules = ConcurrentHashMap.newKeySet();

    public record MappingResult(List<VacancyUpsertDto> vacancies, int skipped) {}

    public MappingResult mapAll(List<TrudvsemVacancyDto> source) {
        List<VacancyUpsertDto> result = new ArrayList<>(source.size());
        int skipped = 0;
        for (TrudvsemVacancyDto vacancy : source) {
            Optional<VacancyUpsertDto> mapped = map(vacancy);
            if (mapped.isPresent()) {
                result.add(mapped.get());
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            log.warn("Mapped {} vacancies, skipped {} invalid", result.size(), skipped);
        }
        return new MappingResult(result, skipped);
    }

    public Optional<VacancyUpsertDto> map(TrudvsemVacancyDto source) {
        if (source == null) {
            return Optional.empty();
        }

        String id = normalizeId(source.id());
        String employerName = clean(source.company() == null ? null : source.company().name());
        String regionCode = clean(source.region() == null ? null : source.region().regionCode());
        String regionName = clean(source.region() == null ? null : source.region().name());
        String jobName = clean(source.jobName());
        String url = clean(source.vacUrl());
        Instant modifiedAt = parseModifiedAt(source.dateModify());

        List<String> problems = new ArrayList<>();
        if (id == null) problems.add("id");
        if (employerName == null) problems.add("employerName");
        if (regionCode == null) problems.add("regionCode");
        if (regionName == null) problems.add("regionName");
        if (jobName == null) problems.add("jobName");
        if (url == null) problems.add("url");
        if (modifiedAt == null) problems.add("modifiedAt");
        if (!problems.isEmpty()) {
            log.warn("Skipping vacancy {}: missing or invalid {}", source.id(), problems);
            return Optional.empty();
        }

        String schedule = clean(source.schedule());
        Integer experience = source.requirement() == null ? null : source.requirement().experience();

        return Optional.of(new VacancyUpsertDto(
                id,
                employerName,
                regionCode,
                regionName,
                jobName,
                nonNegativeOrNull(source.salaryMin()),
                nonNegativeOrNull(source.salaryMax()),
                schedule,
                resolveScheduleCode(schedule),
                null,   // тип занятости в ответе trudvsem.ru отсутствует
                nonNegativeOrNull(experience),
                clean(source.duty()),
                clean(source.requirements()),
                url,
                modifiedAt
        ));
    }

    public String resolveScheduleCode(String schedule) {
        if (schedule == null) {
            return null;
        }
        String text = schedule.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replaceAll("\\s*/\\s*", "/")
                .replaceAll("\\s+", " ")
                .trim();

        String code = getCode(text);

        if (code == null && unknownSchedules.add(text)) {
            log.warn("Unknown schedule value from API: '{}'", schedule);
        }
        return code;
    }

    private static String getCode(String text) {
        String code;
        if (text.contains("вахт")) {
            code = "rotation";
        } else if (text.contains("гибк")) {
            code = "flex";
        } else if (text.contains("сменн")) {
            code = "shift";
        } else if (text.contains("ненормирован")) {
            code = "irregular";
        } else if (text.contains("неполн")) {
            code = "part";
        } else if (text.contains("полный")) {
            code = "full";
        } else {
            code = null;
        }
        return code;
    }

    private static String normalizeId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        return UUID_PATTERN.matcher(trimmed).matches() ? trimmed.toLowerCase(Locale.ROOT) : null;
    }

    private static Instant parseModifiedAt(String dateModify) {
        try {
            return TrudvsemDateParser.parse(dateModify);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String clean(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Integer nonNegativeOrNull(Integer value) {
        return value == null || value < 0 ? null : value;
    }
}