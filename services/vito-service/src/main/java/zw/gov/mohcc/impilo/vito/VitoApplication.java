package zw.gov.mohcc.impilo.vito;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VitoApplication {
    public static void main(String[] args) {
        SpringApplication.run(VitoApplication.class, args);
    }
}
