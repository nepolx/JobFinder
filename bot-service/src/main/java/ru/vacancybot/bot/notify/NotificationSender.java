package ru.vacancybot.bot.notify;

import org.springframework.stereotype.Component;
import ru.vacancybot.bot.domain.Vacancy;
import ru.vacancybot.bot.domain.VacancyChange;
import ru.vacancybot.bot.telegram.TelegramClient;
import ru.vacancybot.bot.view.Keyboards;
import ru.vacancybot.bot.view.MessageFormatter;

/**
 * Исходящие уведомления (Push Payload). Это точка, которую вызывает Notifier/Worker напарника.
 * Лимиты Telegram (≈30 сообщений/с на бота, ≈1/с в один чат) и повторы — зона ответственности вызывающей стороны:
 * при 429 {@code TelegramApiException#getRetryAfterSeconds()} подскажет паузу, при 403 пользователь заблокировал бота.
 */
@Component
public class NotificationSender {

    private final TelegramClient tg;
    private final MessageFormatter fmt;

    public NotificationSender(TelegramClient tg, MessageFormatter fmt) {
        this.tg = tg;
        this.fmt = fmt;
    }

    /** Уведомление о новой вакансии с кнопками [В избранное] [Отписаться]. Личный чат: chat_id == telegram_id. */
    public void sendNewVacancy(long telegramId, Vacancy vacancy, long subscriptionId) {
        tg.sendMessage(telegramId, fmt.newVacancy(vacancy), Keyboards.newVacancy(vacancy.id(), subscriptionId));
    }

    /** Уведомление об изменении условий или закрытии вакансии из избранного. */
    public void sendVacancyChanged(long telegramId, VacancyChange change) {
        tg.sendMessage(telegramId, fmt.vacancyChanged(change), null);
    }
}
