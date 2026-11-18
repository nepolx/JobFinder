package ru.vacancybot.db.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Превращает исключения в понятные HTTP-ответы вида {"error": "..."}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // неверные входные данные (плохой UUID, неизвестный статус, неверный формат опыта)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // например, вакансии с таким id нет в таблице vacancies (внешний ключ)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException e) {
        return build(HttpStatus.CONFLICT,
                "Нарушено ограничение базы данных (возможно, вакансии с таким id нет)");
    }

    private ResponseEntity<Map<String, String>> build(HttpStatus status, String message) {
        String text = message != null ? message : status.getReasonPhrase();
        return ResponseEntity.status(status).body(Map.of("error", text));
    }
}