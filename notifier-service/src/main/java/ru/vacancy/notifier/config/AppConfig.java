package ru.vacancy.notifier.config;

import java.net.http.HttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import ru.vacancy.notifier.db.NotificationRepository;
import ru.vacancy.notifier.limit.ChatRateLimiter;
import ru.vacancy.notifier.limit.CompositeRateLimiter;
import ru.vacancy.notifier.limit.RedisRateLimiter;
import ru.vacancy.notifier.mq.EventMessageProcessor;
import ru.vacancy.notifier.mq.EventParser;
import ru.vacancy.notifier.mq.JacksonEventParser;
import ru.vacancy.notifier.retry.RetryExecutor;
import ru.vacancy.notifier.service.NotificationService;
import ru.vacancy.notifier.telegram.HttpTelegramSender;
import ru.vacancy.notifier.telegram.TelegramSender;
import ru.vacancy.notifier.util.Sleeper;

@Configuration
public class AppConfig {
    @Bean
    Queue eventsQueue(NotifierProperties p) {
        return QueueBuilder.durable(p.eventsQueue())
                .deadLetterExchange("")                     
                .deadLetterRoutingKey(p.retryQueue())       
                .build();
    }

    @Bean
    Queue retryQueue(NotifierProperties p) {
        return QueueBuilder.durable(p.retryQueue())
                .ttl(p.retryDelayMs())                      
                .deadLetterExchange("")
                .deadLetterRoutingKey(p.eventsQueue())
                .build();
    }
    
    @Bean
    ContainerCustomizer<SimpleMessageListenerContainer> shutdownCustomizer() {
        return container -> container.setShutdownTimeout(30_000);
    }

    @Bean
    TelegramSender telegramSender(NotifierProperties p, ObjectMapper mapper) {
        return new HttpTelegramSender(p.telegramBotToken(), HttpClient.newHttpClient(), mapper, p.telegramApiBaseUrl());
    }

    @Bean
    ChatRateLimiter chatRateLimiter(StringRedisTemplate redis, NotifierProperties p) {
        return new CompositeRateLimiter(
                new RedisRateLimiter(redis, p.perChatRate(), 1, "rl", Sleeper.REAL),
                new RedisRateLimiter(redis, p.globalRate(), p.globalBurst(), "rl", Sleeper.REAL));
    }

    @Bean
    ExecutorService sendExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    NotificationService notificationService(NotificationRepository repo, TelegramSender sender,
            ChatRateLimiter limiter, ExecutorService sendExecutor,
            NotifierProperties p) {
        return new NotificationService(repo, sender, limiter,
                RetryExecutor.withDefaults(p.maxAttempts()), sendExecutor, p.sendConcurrency(),
                p.notifySubscribersOnUpdate());
    }

    @Bean
    EventParser eventParser(ObjectMapper mapper) {
        return new JacksonEventParser(mapper);
    }

    @Bean
    EventMessageProcessor eventMessageProcessor(EventParser parser, NotificationService service,
            NotifierProperties p) {
        return new EventMessageProcessor(parser, service, p.maxRedeliveries());
    }
}
