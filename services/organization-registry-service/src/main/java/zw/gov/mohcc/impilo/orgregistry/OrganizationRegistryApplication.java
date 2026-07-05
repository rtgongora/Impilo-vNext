package zw.gov.mohcc.impilo.orgregistry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableKafka
public class OrganizationRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrganizationRegistryApplication.class, args);
    }
}
