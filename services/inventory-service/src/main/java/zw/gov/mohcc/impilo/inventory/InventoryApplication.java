package zw.gov.mohcc.impilo.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import zw.gov.mohcc.impilo.inventory.config.InventoryProperties;

/**
 * Entry point for the Inventory &amp; Supply Chain Service.
 *
 * <p>Manages facility-level inventory including stores, bins, an append-only
 * stock ledger with on-hand projections, FEFO picking, stock counts,
 * internal requisitions, shift handovers, and eLMIS reconciliation.
 * Publishes domain events via the outbox pattern to Kafka.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(InventoryProperties.class)
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
