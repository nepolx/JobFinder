package ru.vacancy.notifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "notifier")
public record NotifierProperties(
        String telegramBotToken,
        @DefaultValue("https://api.telegram.org") String telegramApiBaseUrl,
        @DefaultValue("vacancy.events") String eventsQueue,
        @DefaultValue("vacancy.events.retry") String retryQueue,
        @DefaultValue("60000") int retryDelayMs,          
        @DefaultValue("5") int maxRedeliveries,           
        @DefaultValue("25") double globalRate,            
        @DefaultValue("5") int globalBurst,
        @DefaultValue("1") double perChatRate,            
        @DefaultValue("10") int sendConcurrency,          
        @DefaultValue("5") int maxAttempts,               
        @DefaultValue("true") boolean notifySubscribersOnUpdate) { 
}
