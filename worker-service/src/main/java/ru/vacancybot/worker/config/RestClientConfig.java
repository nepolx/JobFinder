package ru.vacancybot.worker.config;

import ru.vacancybot.worker.client.trudvsem.TrudvsemApiProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({TrudvsemApiProperties.class, SyncProperties.class})
public class RestClientConfig {

    @Bean
    public RestClient trudvsemRestClient(
            RestClient.Builder builder,
            TrudvsemApiProperties properties
    ) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }
}