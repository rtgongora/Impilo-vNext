package zw.gov.mohcc.impilo.ndr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NdrServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NdrServiceApplication.class, args);
    }
}
