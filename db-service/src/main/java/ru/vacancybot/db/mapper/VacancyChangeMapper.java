package ru.vacancybot.db.mapper;

import ru.vacancybot.common.dto.VacancyChangeDto;
import ru.vacancybot.db.entity.VacancyChange;

/** VacancyChange (строка истории) в VacancyChangeDto (то, что уходит по сети). */
public final class VacancyChangeMapper {

    private VacancyChangeMapper() {
    }

    public static VacancyChangeDto toDto(VacancyChange c) {
        return new VacancyChangeDto(
                c.getId(),
                c.getChangedAt(),
                c.getVacancyId().toString(),
                c.getChangeType(),
                c.getChanges());
    }
}