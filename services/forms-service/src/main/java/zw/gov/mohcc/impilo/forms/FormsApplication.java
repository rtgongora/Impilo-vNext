package zw.gov.mohcc.impilo.forms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FormsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FormsApplication.class, args);
    }
}
