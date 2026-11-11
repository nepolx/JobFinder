package ru.vacancybot.worker.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.vacancybot.worker.client.dbservice.DbServiceClient;
import ru.vacancybot.worker.config.EventsProperties;
import ru.vacancybot.worker.dto.dbservice.ChangeKey;
import ru.vacancybot.worker.dto.dbservice.VacancyChangeDto;
import ru.vacancybot.worker.publisher.VacancyEventPublisher.PublishOutcome;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    public record DrainResult(int published, int rejected, boolean brokerFailed) {
    }

    private final DbServiceClient dbClient;
    private final VacancyEventPublisher publisher;
    private final EventsProperties properties;

    public DrainResult drain() {
        int published = 0;
        int rejected = 0;

        while (true) {
            List<VacancyChangeDto> batch = dbClient.fetchUnpublished(properties.batchSize());
            if (batch.isEmpty()) {
                return new DrainResult(published, rejected, false);
            }

            PublishOutcome outcome = publisher.publish(batch);

            // сначала фиксируем то, что точно ушло в очередь
            if (!outcome.confirmed().isEmpty()) {
                dbClient.markPublished(outcome.confirmed());
                published += outcome.confirmed().size();
            }
            for (Map.Entry<ChangeKey, String> entry : outcome.rejected().entrySet()) {
                // ошибка в данных: учитываем попытку, чтобы битое событие не блокировало хвост
                dbClient.markPublishFailed(entry.getKey(), entry.getValue());
                rejected++;
            }

            if (outcome.brokerError() != null) {
                log.warn("Outbox drain stopped: {}. Published {}, events stay in outbox",
                        outcome.brokerError(), published);
                return new DrainResult(published, rejected, true);
            }
            // отклонённые остались бы в следующей выдаче и крутили бы цикл; вернёмся к ним в следующий раз
            if (!outcome.rejected().isEmpty() || batch.size() < properties.batchSize()) {
                if (published > 0 || rejected > 0) {
                    log.info("Outbox drained: published {}, rejected {}", published, rejected);
                }
                return new DrainResult(published, rejected, false);
            }
        }
    }
}