package de.skillkiller.documentdbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocumentdBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentdBackendApplication.class, args);
    }

}
