package zw.gov.mohcc.impilo.msika;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MsikaApplication {
    public static void main(String[] args) {
        SpringApplication.run(MsikaApplication.class, args);
    }
}
