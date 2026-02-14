package zw.gov.mohcc.impilo.rules;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RulesApplication {

    public static void main(String[] args) {
        SpringApplication.run(RulesApplication.class, args);
    }
}
