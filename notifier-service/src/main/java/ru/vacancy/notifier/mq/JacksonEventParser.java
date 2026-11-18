package ru.vacancy.notifier.mq;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.vacancy.notifier.model.ChangeEvent;
import ru.vacancy.notifier.model.InvalidEventException;

public final class JacksonEventParser implements EventParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final ObjectMapper mapper;

    public JacksonEventParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ChangeEvent parse(byte[] body) {
        try {
            return ChangeEvent.fromMap(mapper.readValue(body, MAP_TYPE));
        } catch (IOException e) {
            throw new InvalidEventException("невалидный JSON: " + e.getOriginalMessage(), e);
        }
    }
}
