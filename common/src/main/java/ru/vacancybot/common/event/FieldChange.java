package ru.vacancybot.common.event;

public record FieldChange(String field, String oldValue, String newValue) {}
