package zw.gov.mohcc.impilo.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntegrationHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationHubApplication.class, args);
    }
}
