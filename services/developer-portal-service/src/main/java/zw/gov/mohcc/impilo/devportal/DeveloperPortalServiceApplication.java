package zw.gov.mohcc.impilo.devportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DeveloperPortalServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeveloperPortalServiceApplication.class, args);
    }
}
