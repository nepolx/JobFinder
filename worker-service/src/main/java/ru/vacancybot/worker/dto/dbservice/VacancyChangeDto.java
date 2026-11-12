package ru.vacancybot.worker.dto.dbservice;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record VacancyChangeDto(
        Long changeId,
        Instant changedAt,
        String vacancyId,
        String changeType,
        Map<String, List<Object>> changes
) {}