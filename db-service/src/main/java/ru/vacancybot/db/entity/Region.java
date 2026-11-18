package ru.vacancybot.db.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Регион для выбора в боте. Таблица regions заполняется миграцией, приложение её только читает. */
@Entity
@Table(name = "regions")
public class Region {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    protected Region() {
        // нужен JPA
    }

    public String getCode() { return code; }

    public String getName() { return name; }
}