package ru.vacancybot.bot.view;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.vacancybot.bot.fsm.DialogState;
import ru.vacancybot.bot.fsm.SubscriptionDraft;
import ru.vacancybot.bot.domain.Coded;
import ru.vacancybot.bot.domain.EmploymentType;
import ru.vacancybot.bot.domain.Experience;
import ru.vacancybot.bot.domain.Favorite;
import ru.vacancybot.bot.domain.ScheduleType;
import ru.vacancybot.bot.domain.Subscription;
import ru.vacancybot.bot.domain.SubscriptionCriteria;
import ru.vacancybot.bot.domain.Vacancy;
import ru.vacancybot.bot.domain.VacancyChange;

/** Все тексты бота. Чистые функции — удобно тестировать. */
@Component
public class MessageFormatter {

    public static final String NOT_IMPORTANT = "не важно";
    public static final String NOTHING_FOUND_REGION = "Регион не найден, проверьте написание";
    public static final String NEED_ONE_PARAM = "Укажите хотя бы один параметр поиска";
    public static final String USE_BUTTONS = "Пожалуйста, выберите вариант с помощью кнопок под вопросом.";
    public static final String CONFIRM_WITH_BUTTONS = "Нажмите «Создать подписку» или «Отмена».";
    public static final String SUBSCRIPTION_CREATED = "Подписка успешно создана!";
    public static final String SUBSCRIPTION_UPDATED = "Изменения в подписке сохранены.";
    public static final String SUBSCRIPTION_CANCELLED = "Создание подписки отменено.";
    public static final String UNKNOWN_INPUT = "Я вас не понял. Список команд: /help";
    public static final String UNKNOWN_COMMAND = "Неизвестная команда. Список команд: /help";
    public static final String NO_SUBSCRIPTIONS = "У вас пока нет подписок. Создайте первую: /subscribe";
    public static final String NO_FAVORITES = "В избранном пусто. Нажмите «В избранное» под вакансией в уведомлении.";
    public static final String SERVICE_ERROR = "Что-то пошло не так. Попробуйте ещё раз чуть позже.";
    public static final String SUBSCRIPTION_CARD_PREFIX = "Подписка №";

    private final String supportContact;

    public MessageFormatter(@Value("${bot.support-contact:}") String supportContact) {
        this.supportContact = supportContact == null ? "" : supportContact.trim();
    }

    // ---------- команды ----------

    public String welcome() {
        return "Привет! Я JobFinder — слежу за вакансиями на портале «Работа в России» (trudvsem.ru) "
                + "и пишу вам, когда появляется подходящая вакансия или меняются условия в избранной.\n\n"
                + "Что я умею:\n"
                + "/subscribe — создать подписку на поиск\n"
                + "/subscriptions — мои подписки\n"
                + "/favorites — избранные вакансии\n"
                + "/help — справка";
    }

    public String help() {
        String text = "Как это работает:\n"
                + "1. /subscribe — ответьте на вопросы мастера. Любой вопрос можно пропустить, "
                + "но нужен хотя бы один параметр.\n"
                + "2. Когда на портале появится подходящая вакансия, я пришлю ссылку.\n"
                + "3. Нажмите «В избранное» под вакансией, и я сообщу, если изменятся зарплата, график, "
                + "требования или вакансию закроют.\n\n"
                + "Формат поиска:\n"
                + "• название — текстом, до 100 символов;\n"
                + "• зарплата — целое число в рублях, например 80000;\n"
                + "• ключевые слова — через запятую, до 200 символов.\n\n"
                + "Управление: /subscriptions (изменить, пауза, удалить) и /favorites (пометки «откликнулся», «жду ответа»).";
        if (!supportContact.isEmpty()) {
            text += "\n\nПоддержка: " + supportContact;
        }
        return text;
    }

    // ---------- вопросы мастера ----------

    public String question(DialogState state) {
        return switch (state) {
            case SEARCH_TITLE -> "Введите название вакансии (например: Java разработчик):";
            case SEARCH_REGION -> "Введите регион поиска (например: Санкт-Петербург или Москва):";
            case SEARCH_SALARY -> "Укажите минимальную зарплату в рублях (например: 80000):";
            case SEARCH_EXPERIENCE -> "Выберите требуемый опыт работы:";
            case SEARCH_SCHEDULE -> "Выберите график работы:";
            case SEARCH_EMPLOYMENT -> "Выберите тип занятости:";
            case SEARCH_KEYWORDS -> "Введите ключевые слова через запятую (например: Spring, SQL, Kafka):";
            default -> throw new IllegalArgumentException("No question for " + state);
        };
    }

    public String editingIntro(long subscriptionId) {
        return "Редактирование подписки №" + subscriptionId + ". Ответьте на вопросы заново: "
                + "пропущенный параметр будет считаться «" + NOT_IMPORTANT + "».";
    }

    /** Сводка перед подтверждением. */
    public String summary(SubscriptionDraft draft, String regionName, boolean editing) {
        SubscriptionCriteria c = draft.toCriteria();
        return "Проверьте параметры подписки:\n"
                + criteriaLines(c, regionName)
                + "\n\n"
                + (editing ? "Сохранить изменения с этими параметрами?" : "Создать подписку с этими параметрами?");
    }

    // ---------- карточки ----------

    public String subscriptionCard(Subscription s, String regionName) {
        return SUBSCRIPTION_CARD_PREFIX + s.id() + " (" + (s.active() ? "активна" : "на паузе") + ")\n"
                + criteriaLines(s.criteria(), regionName);
    }

    public String favoriteCard(Favorite f) {
        Vacancy v = f.vacancy();
        StringBuilder sb = new StringBuilder();
        sb.append(v.jobName()).append('\n');
        sb.append("Работодатель: ").append(v.employerName()).append('\n');
        sb.append("Регион: ").append(v.regionName()).append('\n');
        sb.append("Зарплата: ").append(salary(v.salaryMin(), v.salaryMax())).append('\n');
        if (hasText(v.schedule())) {
            sb.append("График: ").append(v.schedule()).append('\n');
        }
        sb.append("Статус: ").append(f.status().label()).append('\n');
        sb.append("Ссылка: ").append(v.url());
        return sb.toString();
    }

    private String criteriaLines(SubscriptionCriteria c, String regionName) {
        return "• Должность: " + orNotImportant(c.title()) + "\n"
                + "• Регион: " + orNotImportant(regionName) + "\n"
                + "• Мин. зарплата: " + (c.salaryMin() == null ? NOT_IMPORTANT : money(c.salaryMin()) + " руб.") + "\n"
                + "• Опыт: " + (c.experienceCode() == null ? NOT_IMPORTANT : Coded.labelOrCode(Experience.class, c.experienceCode())) + "\n"
                + "• График: " + (c.scheduleCode() == null ? NOT_IMPORTANT : Coded.labelOrCode(ScheduleType.class, c.scheduleCode())) + "\n"
                + "• Занятость: " + (c.employmentCode() == null ? NOT_IMPORTANT : Coded.labelOrCode(EmploymentType.class, c.employmentCode())) + "\n"
                + "• Ключевые слова: " + orNotImportant(c.keywords());
    }

    // ---------- уведомления (Push Payload) ----------

    public String newVacancy(Vacancy v) {
        StringBuilder sb = new StringBuilder("Найдена новая вакансия по вашей подписке!\n");
        sb.append("Должность: ").append(v.jobName()).append('\n');
        sb.append("Работодатель: ").append(v.employerName()).append('\n');
        sb.append("Регион: ").append(v.regionName()).append('\n');
        sb.append("Зарплата: ").append(salary(v.salaryMin(), v.salaryMax())).append('\n');
        if (hasText(v.schedule())) {
            sb.append("График: ").append(v.schedule()).append('\n');
        }
        sb.append("Ссылка на вакансию: ").append(v.url());
        return sb.toString();
    }

    public String vacancyChanged(VacancyChange change) {
        Vacancy v = change.vacancy();
        StringBuilder sb = new StringBuilder("Изменение в избранной вакансии!\n");
        sb.append("Должность: ").append(v.jobName()).append('\n');
        sb.append("Работодатель: ").append(v.employerName()).append('\n');
        sb.append("Что изменилось:\n");
        for (VacancyChange.FieldChange fc : change.changes()) {
            sb.append("• ").append(fc.field()).append(": была ").append(fc.before())
                    .append(" ➔ стала ").append(fc.after()).append('\n');
        }
        sb.append("• Статус: ").append(change.closed() ? "Закрыта" : "Открыта").append('\n');
        sb.append("Ссылка: ").append(v.url());
        return sb.toString();
    }

    // ---------- вспомогательное ----------

    /** 120000 → «120 000». */
    public static String money(int value) {
        String digits = Integer.toString(value);
        StringBuilder sb = new StringBuilder();
        int first = digits.length() % 3;
        if (first > 0) {
            sb.append(digits, 0, first);
        }
        for (int i = first; i < digits.length(); i += 3) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(digits, i, i + 3);
        }
        return sb.toString();
    }

    /** «от 120 000 руб.», «до 90 000 руб.», «от 80 000 до 120 000 руб.»; 0 и null считаются «не указано». */
    public static String salary(Integer min, Integer max) {
        boolean hasMin = min != null && min > 0;
        boolean hasMax = max != null && max > 0;
        if (hasMin && hasMax) {
            return "от " + money(min) + " до " + money(max) + " руб.";
        }
        if (hasMin) {
            return "от " + money(min) + " руб.";
        }
        if (hasMax) {
            return "до " + money(max) + " руб.";
        }
        return "не указана";
    }

    private static String orNotImportant(String value) {
        return hasText(value) ? value : NOT_IMPORTANT;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
