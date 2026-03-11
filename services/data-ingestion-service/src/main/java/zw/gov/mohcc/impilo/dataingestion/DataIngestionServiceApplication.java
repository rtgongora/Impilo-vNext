package zw.gov.mohcc.impilo.dataingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataIngestionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataIngestionServiceApplication.class, args);
    }
}
