package zw.gov.mohcc.impilo.pct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.pct.config.PctProperties;
import zw.gov.mohcc.impilo.realtime.RealtimeCoreConfiguration;

/**
 * Entry point for the PCT (Patient Care Tracker) service.
 *
 * <p>Manages patient journeys through facility workflows: arrival, triage,
 * queueing, encounters, admissions, transfers, discharges, and death-case
 * processing. Publishes domain events via the outbox pattern to Kafka.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PctProperties.class)
@Import(RealtimeCoreConfiguration.class)
public class PctApplication {

    public static void main(String[] args) {
        SpringApplication.run(PctApplication.class, args);
    }
}
