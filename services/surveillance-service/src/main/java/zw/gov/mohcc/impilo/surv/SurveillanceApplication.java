package zw.gov.mohcc.impilo.surv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SurveillanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SurveillanceApplication.class, args);
    }
}
