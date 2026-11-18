package ru.vacancybot.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vacancybot.db.entity.Region;

import java.util.List;

public interface RegionRepository extends JpaRepository<Region, String> {

    /** Все регионы по алфавиту (для кнопок в боте). */
    List<Region> findAllByOrderByNameAsc();
}