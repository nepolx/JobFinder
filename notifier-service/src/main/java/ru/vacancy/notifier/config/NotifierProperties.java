package ru.vacancy.notifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "notifier")
public record NotifierProperties(
        String telegramBotToken,
        @DefaultValue("vacancy.events") String eventsQueue,
        @DefaultValue("vacancy.events.retry") String retryQueue,
        @DefaultValue("60000") int retryDelayMs,          // через сколько сообщение вернётся из retry-очереди
        @DefaultValue("5") int maxRedeliveries,           // кругов повтора, после которых сдаёмся
        @DefaultValue("25") double globalRate,            // сообщений/с на все копии (лимит Telegram ~30)
        @DefaultValue("5") int globalBurst,
        @DefaultValue("1") double perChatRate,            // сообщений/с в один чат
        @DefaultValue("10") int sendConcurrency,          // получателей одного события параллельно
        @DefaultValue("5") int maxAttempts,               // попыток отправки одному получателю
        @DefaultValue("true") boolean notifySubscribersOnUpdate) { // при изменении вакансии слать подписчикам, не видевшим её
}
