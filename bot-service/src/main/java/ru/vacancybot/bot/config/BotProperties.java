package ru.vacancybot.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки бота (префикс bot, см. application.yml):
 * token/username/webhook-url — как в .env.example; webhook-secret и support-contact необязательны.
 */
@ConfigurationProperties(prefix = "bot")
public record BotProperties(
        String token,
        String username,
        String webhookUrl,
        String webhookSecret,
        String apiBaseUrl) {

    /** Путь, на котором контроллер принимает update. BOT_WEBHOOK_URL должен заканчиваться на него. */
    public static final String WEBHOOK_PATH = "/webhook";

    private static final String PLACEHOLDER_TOKEN = "changeme";

    public BotProperties {
        token = token == null ? "" : token.trim();
        username = username == null ? "" : username.trim();
        webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        apiBaseUrl = (apiBaseUrl == null || apiBaseUrl.isBlank()) ? "https://api.telegram.org" : apiBaseUrl.trim();
    }

    /** Токен реально задан (заглушка «changeme» из application.yml не считается). */
    public boolean hasToken() {
        return !token.isEmpty() && !PLACEHOLDER_TOKEN.equals(token);
    }

    /** Webhook-адрес реально задан (пример https://example.com/webhook из application.yml не считается). */
    public boolean hasWebhookUrl() {
        return !webhookUrl.isEmpty() && !webhookUrl.contains("example.com");
    }
}
