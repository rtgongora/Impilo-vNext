package zw.gov.mohcc.impilo.dags;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DagsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DagsApplication.class, args);
    }
}
