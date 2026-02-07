package zw.gov.mohcc.impilo.oros;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.oros.config.OrosProperties;

/**
 * Entry point for the OROS (Orders &amp; Results Orchestration Service).
 *
 * <p>Manages the full lifecycle of clinical orders — placement, routing to
 * internal or external fulfillment systems (LIMS, PACS, pharmacy), result
 * capture, acknowledgement workflows, SLA tracking, and reconciliation.
 * Publishes domain events via the outbox pattern to Kafka.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OrosProperties.class)
public class OrosApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrosApplication.class, args);
    }
}
