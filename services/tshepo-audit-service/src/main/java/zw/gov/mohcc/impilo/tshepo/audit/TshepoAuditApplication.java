package zw.gov.mohcc.impilo.tshepo.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TshepoAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(TshepoAuditApplication.class, args);
    }
}
