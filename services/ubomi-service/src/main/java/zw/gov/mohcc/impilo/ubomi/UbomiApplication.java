package zw.gov.mohcc.impilo.ubomi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UbomiApplication {
    public static void main(String[] args) {
        SpringApplication.run(UbomiApplication.class, args);
    }
}
