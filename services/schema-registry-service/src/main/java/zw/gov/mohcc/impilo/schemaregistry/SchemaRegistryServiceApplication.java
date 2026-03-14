package zw.gov.mohcc.impilo.schemaregistry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SchemaRegistryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchemaRegistryServiceApplication.class, args);
    }
}
