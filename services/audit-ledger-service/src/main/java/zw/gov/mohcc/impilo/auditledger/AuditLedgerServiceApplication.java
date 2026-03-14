package zw.gov.mohcc.impilo.auditledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuditLedgerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditLedgerServiceApplication.class, args);
    }
}
