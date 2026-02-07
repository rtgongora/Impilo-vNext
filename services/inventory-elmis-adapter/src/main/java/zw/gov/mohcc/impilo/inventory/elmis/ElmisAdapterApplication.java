package zw.gov.mohcc.impilo.inventory.elmis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import zw.gov.mohcc.impilo.inventory.elmis.config.ElmisAdapterProperties;

/**
 * Entry point for the Inventory eLMIS Adapter service.
 *
 * <p>A lightweight bridge between the inventory service and the national
 * eLMIS (Electronic Logistics Management Information System). Supports
 * multiple connector modes (REST, CSV, Kafka) for stock synchronization,
 * receipt forwarding, and reconciliation data exchange.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(ElmisAdapterProperties.class)
public class ElmisAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElmisAdapterApplication.class, args);
    }
}
