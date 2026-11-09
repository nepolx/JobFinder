package ru.vacancybot.bot.view;

/**
 * Форматы callback_data (лимит Telegram — 64 байта). Префиксы с «_» на конце несут аргумент.
 */
public final class CallbackData {

    // мастер создания подписки
    public static final String SUB_SKIP = "sub_skip";
    public static final String SUB_CONFIRM = "sub_confirm";
    public static final String SUB_CANCEL = "sub_cancel";
    public static final String SUB_REG = "sub_reg_";
    public static final String SUB_SAL = "sub_sal_";
    public static final String SUB_EXP = "sub_exp_";
    public static final String SUB_SCH = "sub_sch_";
    public static final String SUB_EMP = "sub_emp_";

    // управление подписками
    public static final String SUB_DEL = "sub_del_";
    public static final String SUB_PAUSE = "sub_pause_";
    public static final String SUB_RESUME = "sub_resume_";
    public static final String SUB_EDIT = "sub_edit_";

    // избранное
    public static final String FAV_ADD = "fav_add_";
    public static final String FAV_DEL = "fav_del_";
    public static final String FAV_NOTE = "fav_note_";
    /** fav_st_{statusCode}_{vacancyId}; код статуса без «_», поэтому делим по первому «_». */
    public static final String FAV_STATUS = "fav_st_";

    private CallbackData() {
    }

    public static String with(String prefix, Object arg) {
        return prefix + arg;
    }

    public static String favStatus(String statusCode, Object vacancyId) {
        return FAV_STATUS + statusCode + "_" + vacancyId;
    }

    /** Аргумент после префикса или null, если data не начинается с префикса. */
    public static String arg(String data, String prefix) {
        if (data == null || !data.startsWith(prefix)) {
            return null;
        }
        return data.substring(prefix.length());
    }

    /** Относится ли callback к мастеру создания подписки. */
    public static boolean isWizard(String data) {
        return data != null && (data.equals(SUB_SKIP) || data.equals(SUB_CONFIRM) || data.equals(SUB_CANCEL)
                || data.startsWith(SUB_REG) || data.startsWith(SUB_SAL) || data.startsWith(SUB_EXP)
                || data.startsWith(SUB_SCH) || data.startsWith(SUB_EMP));
    }
}
