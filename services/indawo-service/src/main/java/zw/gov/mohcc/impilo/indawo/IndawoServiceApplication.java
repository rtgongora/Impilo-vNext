package zw.gov.mohcc.impilo.indawo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IndawoServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IndawoServiceApplication.class, args);
    }
}
