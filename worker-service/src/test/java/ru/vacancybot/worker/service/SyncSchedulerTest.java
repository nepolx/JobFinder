package ru.vacancybot.worker.service;

import org.junit.jupiter.api.Test;
import ru.vacancybot.worker.exception.DbServiceException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncSchedulerTest {

    private final SyncOrchestrator orchestrator = mock(SyncOrchestrator.class);
    private final SyncScheduler scheduler = new SyncScheduler(orchestrator);

    @Test
    void runsTheOrchestrator() {
        scheduler.runScheduled();

        verify(orchestrator).run();
    }

    @Test
    void failureIsLoggedAndDoesNotEscapeSoSchedulingContinues() {
        when(orchestrator.run()).thenThrow(new DbServiceException("db-service is down", 503, true));

        assertThatCode(scheduler::runScheduled).doesNotThrowAnyException();
    }
}
