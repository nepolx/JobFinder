package ru.vacancybot.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Событие по вакансии: ответ db-service на POST /api/vacancies/sync.
 * changeType = "created" или "updated" (строчными, как в событии RabbitMQ).
 * changes: {имя колонки vacancies: [было, стало]}; для "created" равно null.
 * Пара (changeId, changedAt) однозначно определяет событие и идёт дальше в очередь.
 */
public record VacancyChangeDto(
        Long changeId,
        Instant changedAt,
        String vacancyId,
        String changeType,
        Map<String, List<Object>> changes
) {}