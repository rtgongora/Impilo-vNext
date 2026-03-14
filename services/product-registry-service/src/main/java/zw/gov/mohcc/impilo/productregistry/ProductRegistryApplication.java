package zw.gov.mohcc.impilo.productregistry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Product Registry Service.
 *
 * <p>Manages the product master data registry: product definitions,
 * categories, and lifecycle status. Publishes domain events via the
 * outbox pattern to Kafka.</p>
 */
@SpringBootApplication
@EnableScheduling
public class ProductRegistryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductRegistryApplication.class, args);
    }
}
