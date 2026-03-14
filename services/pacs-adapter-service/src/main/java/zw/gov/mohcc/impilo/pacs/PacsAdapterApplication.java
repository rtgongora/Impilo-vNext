package zw.gov.mohcc.impilo.pacs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the PACS Adapter Service.
 *
 * <p>Handles PACS boundary: imaging metadata ingest, forwarding to Orthanc,
 * and audit trail. Publishes domain events via the outbox pattern to Kafka.</p>
 */
@SpringBootApplication
@EnableScheduling
public class PacsAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(PacsAdapterApplication.class, args);
    }
}
