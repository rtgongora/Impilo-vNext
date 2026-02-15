package zw.gov.mohcc.impilo.secharden;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SecHardenApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecHardenApplication.class, args);
    }
}
