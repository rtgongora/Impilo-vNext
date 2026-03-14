package zw.gov.mohcc.impilo.offlinesync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Offline Sync Service.
 *
 * <p>Handles offline data synchronization with queued action intake,
 * replay/reconciliation, and conflict queues. Publishes domain events
 * via the outbox pattern to Kafka.</p>
 */
@SpringBootApplication
@EnableScheduling
public class OfflineSyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfflineSyncApplication.class, args);
    }
}
