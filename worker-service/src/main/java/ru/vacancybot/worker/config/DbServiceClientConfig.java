package ru.vacancybot.worker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.vacancybot.worker.client.dbservice.DbServiceProperties;

@Configuration
@EnableConfigurationProperties(DbServiceProperties.class)
public class DbServiceClientConfig {

    @Bean
    public RestClient dbServiceRestClient(RestClient.Builder builder, DbServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }
}