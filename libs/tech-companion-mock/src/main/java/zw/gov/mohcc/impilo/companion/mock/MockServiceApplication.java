package zw.gov.mohcc.impilo.companion.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot application demonstrating the tech-companion library.
 *
 * Exposes v1.1 compliant endpoints behind the companion filters.
 * Used to verify the golden contract test suite passes.
 */
@SpringBootApplication
public class MockServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockServiceApplication.class, args);
    }
}
