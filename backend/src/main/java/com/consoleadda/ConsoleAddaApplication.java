package com.consoleadda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ConsoleAddaApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsoleAddaApplication.class, args);
    }
}
