package zw.gov.mohcc.impilo.pharmacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.pharmacy.config.PharmacyProperties;

/**
 * Entry point for the Pharmacy Service.
 *
 * <p>Manages the full lifecycle of pharmacy dispensing workflows, including
 * order acceptance, item picking (FEFO), dispensing, stock movements,
 * formulary enforcement, substitution rules, pickup proof (OTP/QR),
 * and eLMIS reconciliation. Publishes domain events via the outbox
 * pattern to Kafka.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PharmacyProperties.class)
public class PharmacyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PharmacyApplication.class, args);
    }
}
