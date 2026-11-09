package ru.vacancybot.bot.stub;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.vacancybot.bot.domain.VacancyChange;
import ru.vacancybot.bot.notify.NotificationSender;

import java.util.List;

/**
 * Только для разработки: шлёт тестовые уведомления из документации, чтобы проверить кнопки
 * «В избранное» / «Отписаться» без Notifier. Выключен по умолчанию (bot.dev-endpoints=true включает).
 * Не включайте в публичном окружении: любой сможет слать сообщения от имени бота.
 */
@RestController
@ConditionalOnProperty(name = "bot.dev-endpoints", havingValue = "true")
public class DevNotifyController {

    private final NotificationSender sender;

    public DevNotifyController(NotificationSender sender) {
        this.sender = sender;
    }

    @PostMapping("/dev/notify/{telegramId}")
    public String notifyUser(@PathVariable long telegramId) {
        sender.sendNewVacancy(telegramId, SampleVacancies.NEW_VACANCY, 1);
        sender.sendVacancyChanged(telegramId, new VacancyChange(
                SampleVacancies.CHANGED_VACANCY,
                List.of(new VacancyChange.FieldChange("Зарплата", "100 000 руб.", "130 000 руб.")),
                false));
        return "sent";
    }
}
