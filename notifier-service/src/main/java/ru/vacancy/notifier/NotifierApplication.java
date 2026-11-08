package ru.vacancy.notifier;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NotifierApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotifierApplication.class, args);
    }
}
