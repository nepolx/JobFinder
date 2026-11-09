package ru.vacancybot.db.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vacancybot.db.entity.SyncState;

public interface SyncStateRepository extends JpaRepository<SyncState, String> {
    // всё нужное (findById, save) уже есть в JpaRepository
}