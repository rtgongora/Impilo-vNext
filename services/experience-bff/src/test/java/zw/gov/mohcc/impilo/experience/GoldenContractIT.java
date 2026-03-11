package zw.gov.mohcc.impilo.experience;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract IT for experience-bff.
 *
 * Extends GoldenContractSuite (Wave 6 auto-discovery) from tech-companion-harness.
 * Uses Testcontainers PostgreSQL for a real database.
 *
 * Verifies:
 *   - Header enforcement (missing any required header -> 400 + envelope)
 *   - Error envelope format (code, message, details, request_id, correlation_id)
 *   - Idempotency (missing key -> 400; same key + different body -> 409)
 *   - Timeout enforcement (expired X-Client-Timeout-MS -> 504)
 *
 * The suite auto-discovers v1.1 endpoints using Spring's RequestMappingHandlerMapping.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@ActiveProfiles("test")
public class GoldenContractIT extends GoldenContractSuite {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("experience_bff_golden")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/facilities";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/reports/generate";
    }
}
