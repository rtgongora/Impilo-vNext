package zw.gov.mohcc.impilo.datagovernance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataGovernanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataGovernanceServiceApplication.class, args);
    }
}
