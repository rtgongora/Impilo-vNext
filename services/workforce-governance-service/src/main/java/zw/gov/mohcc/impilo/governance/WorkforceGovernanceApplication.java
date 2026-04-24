package zw.gov.mohcc.impilo.governance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WorkforceGovernanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkforceGovernanceApplication.class, args);
    }
}
