package zw.gov.mohcc.impilo.gl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableKafka
public class GeneralLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GeneralLedgerApplication.class, args);
    }
}
