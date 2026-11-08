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

    // --- RabbitMQ: очереди объявляются при старте (RabbitAdmin) ---

    @Bean
    Queue eventsQueue(NotifierProperties p) {
        return QueueBuilder.durable(p.eventsQueue())
                .deadLetterExchange("")                     // default exchange
                .deadLetterRoutingKey(p.retryQueue())       // отклонённые уходят в retry
                .build();
    }

    @Bean
    Queue retryQueue(NotifierProperties p) {
        return QueueBuilder.durable(p.retryQueue())
                .ttl(p.retryDelayMs())                      // полежать и вернуться
                .deadLetterExchange("")
                .deadLetterRoutingKey(p.eventsQueue())
                .build();
    }

    /** При остановке ждём до 30 с, пока дообработаются события, уже взятые из очереди. */
    @Bean
    ContainerCustomizer<SimpleMessageListenerContainer> shutdownCustomizer() {
        return container -> container.setShutdownTimeout(30_000);
    }

    // --- Логика ---

    @Bean
    TelegramSender telegramSender(NotifierProperties p, ObjectMapper mapper) {
        return new HttpTelegramSender(p.telegramBotToken(), HttpClient.newHttpClient(), mapper);
    }

    @Bean
    ChatRateLimiter chatRateLimiter(StringRedisTemplate redis, NotifierProperties p) {
        return new CompositeRateLimiter(
                new RedisRateLimiter(redis, p.perChatRate(), 1, "rl", Sleeper.REAL),
                new RedisRateLimiter(redis, p.globalRate(), p.globalBurst(), "rl", Sleeper.REAL));
    }

    /** Виртуальные потоки: тысячи ожидающих лимита отправок не занимают системные потоки. */
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
