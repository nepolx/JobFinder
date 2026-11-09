package ru.vacancybot.bot.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Клиент к db-service создаётся, только когда заглушки выключены (bot.stubs.enabled=false). */
@Configuration
@ConditionalOnProperty(name = "bot.stubs.enabled", havingValue = "false")
public class DbServiceConfig {

    @Bean
    public DbServiceClient dbServiceClient(@Value("${clients.db-service.base-url}") String baseUrl, ObjectMapper mapper) {
        return new DbServiceClient(baseUrl, mapper);
    }
}
