package ru.vacancybot.worker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "worker.run.enabled", havingValue = "true", matchIfMissing = true)
public class SyncScheduler {

    private final SyncOrchestrator orchestrator;

    public SyncScheduler(SyncOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Scheduled(
            fixedDelayString = "${worker.run.interval-ms:300000}",
            initialDelayString = "${worker.run.initial-delay-ms:30000}")
    public void runScheduled() {
        try {
            orchestrator.run();
        } catch (RuntimeException e) {
            // курсор остался на последнем успешно записанном окне: следующий запуск продолжит оттуда
            log.error("Sync run failed, will retry at the next scheduled run", e);
        }
    }
}