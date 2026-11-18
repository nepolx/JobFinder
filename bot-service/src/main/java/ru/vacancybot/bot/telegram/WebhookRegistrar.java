package ru.vacancybot.bot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.vacancybot.bot.config.BotProperties;
import ru.vacancybot.bot.telegram.dto.BotCommand;

import java.util.List;

/** При старте регистрирует меню команд и webhook в Telegram. Ошибки только логируются: сервис всё равно поднимается. */
@Component
public class WebhookRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WebhookRegistrar.class);

    private final TelegramClient tg;
    private final BotProperties props;

    public WebhookRegistrar(TelegramClient tg, BotProperties props) {
        this.tg = tg;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.hasToken()) {
            log.warn("bot.token не задан (BOT_TOKEN): бот не подключён к Telegram");
            return;
        }
        try {
            tg.setMyCommands(List.of(
                    new BotCommand("subscribe", "Создать подписку на вакансии"),
                    new BotCommand("subscriptions", "Мои подписки"),
                    new BotCommand("favorites", "Избранные вакансии"),
                    new BotCommand("help", "Справка")));
        } catch (RuntimeException e) {
            log.error("Не удалось зарегистрировать меню команд: {}", e.getMessage());
        }
        if (!props.hasWebhookUrl()) {
            log.warn("bot.webhook-url не задан (BOT_WEBHOOK_URL): webhook не зарегистрирован");
            return;
        }
        if (!props.webhookUrl().endsWith(BotProperties.WEBHOOK_PATH)) {
            log.warn("BOT_WEBHOOK_URL должен заканчиваться на {}, иначе Telegram не достучится до контроллера",
                    BotProperties.WEBHOOK_PATH);
        }
        try {
            tg.setWebhook(props.webhookUrl(), props.webhookSecret());
            log.info("Webhook зарегистрирован: {}", props.webhookUrl());
        } catch (RuntimeException e) {
            log.error("Не удалось зарегистрировать webhook: {}", e.getMessage());
        }
    }
}
