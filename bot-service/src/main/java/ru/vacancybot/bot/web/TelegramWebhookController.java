package ru.vacancybot.bot.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ru.vacancybot.bot.config.BotProperties;
import ru.vacancybot.bot.handler.UpdateRouter;
import ru.vacancybot.bot.telegram.dto.Update;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Приём update от Telegram (webhook). */
@RestController
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);
    static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final UpdateRouter router;
    private final BotProperties props;

    public TelegramWebhookController(UpdateRouter router, BotProperties props) {
        this.router = router;
        this.props = props;
    }

    @PostMapping(BotProperties.WEBHOOK_PATH)
    public ResponseEntity<Void> onUpdate(
            @RequestHeader(value = SECRET_HEADER, required = false) String secret,
            @RequestBody Update update) {
        if (!secretMatches(secret)) {
            log.warn("Webhook call with a wrong secret token rejected");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Отвечаем 200 всегда: роутер сам ловит ошибки, а повторная доставка Telegram только усугубит ситуацию.
        router.handle(update);
        return ResponseEntity.ok().build();
    }

    private boolean secretMatches(String received) {
        String expected = props.webhookSecret();
        if (expected.isEmpty()) {
            return true; // секрет не настроен (локальная разработка)
        }
        if (received == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), received.getBytes(StandardCharsets.UTF_8));
    }
}
