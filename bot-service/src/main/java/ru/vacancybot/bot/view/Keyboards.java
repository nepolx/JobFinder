package ru.vacancybot.bot.view;

import ru.vacancybot.bot.fsm.DialogState;
import ru.vacancybot.bot.domain.Coded;
import ru.vacancybot.bot.domain.EmploymentType;
import ru.vacancybot.bot.domain.Experience;
import ru.vacancybot.bot.domain.Favorite;
import ru.vacancybot.bot.domain.FavoriteStatus;
import ru.vacancybot.bot.domain.Region;
import ru.vacancybot.bot.domain.ScheduleType;
import ru.vacancybot.bot.domain.Subscription;
import ru.vacancybot.bot.telegram.dto.InlineKeyboardButton;
import ru.vacancybot.bot.telegram.dto.InlineKeyboardMarkup;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Построение inline-клавиатур. */
public final class Keyboards {

    private static final int[] SALARY_OPTIONS = {50_000, 80_000, 100_000, 150_000};

    private Keyboards() {
    }

    public static InlineKeyboardButton button(String text, String callbackData) {
        return new InlineKeyboardButton(text, callbackData);
    }

    private static InlineKeyboardMarkup rows(List<List<InlineKeyboardButton>> rows) {
        return new InlineKeyboardMarkup(rows);
    }

    private static List<InlineKeyboardButton> row(InlineKeyboardButton... buttons) {
        return List.of(buttons);
    }

    private static InlineKeyboardButton skip() {
        return button("Пропустить", CallbackData.SUB_SKIP);
    }

    /** Клавиатура под вопросом мастера. */
    public static InlineKeyboardMarkup forState(DialogState state, List<Region> popularRegions) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        switch (state) {
            case SEARCH_TITLE, SEARCH_KEYWORDS -> {
                // только «Пропустить»
            }
            case SEARCH_REGION -> {
                List<InlineKeyboardButton> quick = new ArrayList<>();
                for (Region r : popularRegions) {
                    quick.add(button(r.name(), CallbackData.with(CallbackData.SUB_REG, r.code())));
                }
                if (!quick.isEmpty()) {
                    rows.add(quick);
                }
            }
            case SEARCH_SALARY -> {
                List<InlineKeyboardButton> amounts = new ArrayList<>();
                for (int amount : SALARY_OPTIONS) {
                    amounts.add(button(MessageFormatter.money(amount), CallbackData.with(CallbackData.SUB_SAL, amount)));
                }
                rows.add(amounts);
            }
            case SEARCH_EXPERIENCE -> addCoded(rows, Experience.class, CallbackData.SUB_EXP, 2);
            case SEARCH_SCHEDULE -> addCoded(rows, ScheduleType.class, CallbackData.SUB_SCH, 1);
            case SEARCH_EMPLOYMENT -> addCoded(rows, EmploymentType.class, CallbackData.SUB_EMP, 1);
            case CONFIRM_SUBSCRIPTION -> {
                rows.add(row(button("Создать подписку", CallbackData.SUB_CONFIRM),
                        button("Отмена", CallbackData.SUB_CANCEL)));
                return rows(rows);
            }
            default -> throw new IllegalArgumentException("No keyboard for " + state);
        }
        rows.add(row(skip()));
        return rows(rows);
    }

    private static <E extends Enum<E> & Coded> void addCoded(List<List<InlineKeyboardButton>> rows,
                                                             Class<E> type, String prefix, int perRow) {
        List<InlineKeyboardButton> current = new ArrayList<>();
        for (E e : type.getEnumConstants()) {
            current.add(button(e.label(), CallbackData.with(prefix, e.code())));
            if (current.size() == perRow) {
                rows.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            rows.add(current);
        }
    }

    /** Кнопки под карточкой подписки. */
    public static InlineKeyboardMarkup subscriptionCard(Subscription s) {
        InlineKeyboardButton toggle = s.active()
                ? button("Пауза", CallbackData.with(CallbackData.SUB_PAUSE, s.id()))
                : button("Возобновить", CallbackData.with(CallbackData.SUB_RESUME, s.id()));
        return rows(List.of(
                row(button("Изменить", CallbackData.with(CallbackData.SUB_EDIT, s.id())), toggle),
                row(button("Удалить", CallbackData.with(CallbackData.SUB_DEL, s.id())))));
    }

    /** Кнопки под карточкой избранной вакансии. */
    public static InlineKeyboardMarkup favoriteCard(Favorite f) {
        String id = f.vacancy().id();
        return rows(List.of(row(
                button("Пометка", CallbackData.with(CallbackData.FAV_NOTE, id)),
                button("Убрать из избранного", CallbackData.with(CallbackData.FAV_DEL, id)))));
    }

    /** Выбор пометки: «Сохранена», «Откликнулся», «Жду ответа». */
    public static InlineKeyboardMarkup favoriteStatuses(String vacancyId) {
        List<InlineKeyboardButton> statuses = new ArrayList<>();
        for (FavoriteStatus st : FavoriteStatus.values()) {
            statuses.add(button(st.label(), CallbackData.favStatus(st.code(), vacancyId)));
        }
        return rows(List.of(statuses));
    }

    /** Кнопки уведомления о новой вакансии: [В избранное] [Отписаться]. */
    public static InlineKeyboardMarkup newVacancy(String vacancyId, long subscriptionId) {
        return rows(List.of(row(
                button("В избранное", CallbackData.with(CallbackData.FAV_ADD, vacancyId)),
                button("Отписаться", CallbackData.with(CallbackData.SUB_DEL, subscriptionId)))));
    }

    /** Копия клавиатуры без кнопок, чей callback_data удовлетворяет условию; пустые строки удаляются. */
    public static InlineKeyboardMarkup without(InlineKeyboardMarkup markup, Predicate<String> drop) {
        if (markup == null || markup.isEmpty()) {
            return InlineKeyboardMarkup.empty();
        }
        List<List<InlineKeyboardButton>> result = new ArrayList<>();
        for (List<InlineKeyboardButton> row : markup.inlineKeyboard()) {
            List<InlineKeyboardButton> kept = new ArrayList<>();
            for (InlineKeyboardButton b : row) {
                if (b.callbackData() == null || !drop.test(b.callbackData())) {
                    kept.add(b);
                }
            }
            if (!kept.isEmpty()) {
                result.add(kept);
            }
        }
        return rows(result);
    }
}
