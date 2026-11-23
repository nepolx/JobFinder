package ru.vacancybot.worker.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import ru.vacancybot.worker.client.dbservice.DbServiceProperties;
import ru.vacancybot.worker.client.trudvsem.TrudvsemApiProperties;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConfigBindingTest {

    private static Binder yml;

    @BeforeAll
    static void loadApplicationYml() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        yml = new Binder(ConfigurationPropertySources.from(sources));
    }

    @Test
    void workerSettingsAreTopLevelAndNotNestedUnderSpring() {
        assertThat(yml.bind("spring.worker.trudvsem.base-url", String.class).isBound())
                .as("блок worker: должен быть на верхнем уровне, а не внутри spring:")
                .isFalse();
        assertThat(yml.bind("worker.trudvsem.base-url", String.class).isBound())
                .as("worker.trudvsem.base-url не найден: проверьте отступы в application.yml")
                .isTrue();
    }

    @Test
    void trudvsemSettingsAreBoundAndSane() {
        BindResult<TrudvsemApiProperties> result = yml.bind("worker.trudvsem", TrudvsemApiProperties.class);

        assertThat(result.isBound()).isTrue();
        TrudvsemApiProperties properties = result.get();
        assertThat(properties.baseUrl()).isNotBlank();
        assertThat(properties.baseUrl()).doesNotContain("/api");
        assertThat(properties.pageSize()).isBetween(1, 100);
        assertThat(properties.connectTimeout()).isNotNull().isPositive();
        assertThat(properties.readTimeout()).isNotNull().isGreaterThanOrEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void dbServiceSettingsAreBound() {
        DbServiceProperties properties = yml.bind("worker.db-service", DbServiceProperties.class).get();

        assertThat(properties.baseUrl()).startsWith("http");
        assertThat(properties.connectTimeout()).isPositive();
        assertThat(properties.readTimeout()).isPositive();
    }

    @Test
    void syncRunAndEventsSettingsAreBoundAndValid() {
        assertThat(yml.bind("worker.sync", SyncProperties.class).isBound()).isTrue();
        assertThat(yml.bind("worker.run", RunProperties.class).isBound()).isTrue();
        EventsProperties events = yml.bind("worker.events", EventsProperties.class).get();
        assertThat(events.queue()).isEqualTo("vacancy.events");
    }

    @Test
    void schedulerPlaceholdersExist() {
        assertThat(yml.bind("worker.run.interval-ms", Long.class).orElse(0L)).isPositive();
        assertThat(yml.bind("worker.run.initial-delay-ms", Long.class).orElse(0L)).isNotNegative();
    }

    @Test
    void rabbitPublisherConfirmsAreEnabled() {
        assertThat(yml.bind("spring.rabbitmq.publisher-confirm-type", String.class).orElse(""))
                .isEqualToIgnoringCase("correlated");
        assertThat(yml.bind("spring.rabbitmq.publisher-returns", Boolean.class).orElse(false)).isTrue();
        assertThat(yml.bind("spring.rabbitmq.template.mandatory", Boolean.class).orElse(false)).isTrue();
    }

    @Test
    void defaultsApplyWhenNothingIsConfigured() {
        Binder empty = new Binder(new MapConfigurationPropertySource());

        SyncProperties sync = empty.bindOrCreate("worker.sync", SyncProperties.class);
        assertThat(sync.windowSize()).isEqualTo(Duration.ofHours(1));
        assertThat(sync.maxReloadAttempts()).isEqualTo(2);
        assertThat(sync.maxRetries()).isEqualTo(3);
        assertThat(sync.retryBaseDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(sync.pauseBetweenRequests()).isEqualTo(Duration.ofMillis(100));
        assertThat(sync.maxWindowRecords()).isEqualTo(10_000);
        assertThat(sync.pageConcurrency()).isEqualTo(3);

        RunProperties run = empty.bindOrCreate("worker.run", RunProperties.class);
        assertThat(run.overlap()).isEqualTo(Duration.ofHours(2));
        assertThat(run.initialLookback()).isEqualTo(Duration.ofHours(24));
        assertThat(run.maxCatchUp()).isEqualTo(Duration.ofHours(48));

        EventsProperties events = empty.bindOrCreate("worker.events", EventsProperties.class);
        assertThat(events.queue()).isEqualTo("vacancy.events");
        assertThat(events.batchSize()).isEqualTo(200);
        assertThat(events.confirmTimeout()).isEqualTo(Duration.ofSeconds(10));

        DbServiceProperties db = empty.bindOrCreate("worker.db-service", DbServiceProperties.class);
        assertThat(db.baseUrl()).isEqualTo("http://localhost:8081");
        assertThat(db.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(db.readTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
