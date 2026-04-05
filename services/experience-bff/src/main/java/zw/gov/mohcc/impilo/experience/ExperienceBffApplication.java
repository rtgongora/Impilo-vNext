package zw.gov.mohcc.impilo.experience;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExperienceBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExperienceBffApplication.class, args);
    }
}
