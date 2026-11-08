package ru.vacancy.notifier.model;

public enum ChangeType {
    CREATED("created"),
    UPDATED("updated"),
    CLOSED("closed");   // сейчас Poller такие события не шлёт, но обработка оставлена

    private final String value;

    ChangeType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ChangeType fromValue(Object raw) {
        for (ChangeType t : values()) {
            if (t.value.equals(raw)) {
                return t;
            }
        }
        throw new IllegalArgumentException("неизвестный type: " + raw);
    }
}
