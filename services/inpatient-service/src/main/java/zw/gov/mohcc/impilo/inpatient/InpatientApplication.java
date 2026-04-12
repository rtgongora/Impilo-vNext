package zw.gov.mohcc.impilo.inpatient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Inpatient Service.
 *
 * <p>Manages the inpatient lifecycle: admissions, ward transfers,
 * bed assignments, and discharges. Publishes domain events via the
 * outbox pattern to Kafka.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
public class InpatientApplication {

    public static void main(String[] args) {
        SpringApplication.run(InpatientApplication.class, args);
    }
}
