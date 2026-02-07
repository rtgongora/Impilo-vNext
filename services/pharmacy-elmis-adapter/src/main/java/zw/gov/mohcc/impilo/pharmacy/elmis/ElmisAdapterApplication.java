package zw.gov.mohcc.impilo.pharmacy.elmis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import zw.gov.mohcc.impilo.pharmacy.elmis.config.ElmisAdapterProperties;

/**
 * Entry point for the Pharmacy eLMIS Adapter service.
 *
 * <p>A lightweight bridge between the pharmacy service and the national
 * eLMIS (Electronic Logistics Management Information System). Supports
 * multiple connector modes (REST, CSV, Kafka) for stock synchronization,
 * order forwarding, and reconciliation data exchange.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(ElmisAdapterProperties.class)
public class ElmisAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElmisAdapterApplication.class, args);
    }
}
