package zw.gov.mohcc.impilo.experience;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract IT for experience-bff.
 *
 * Extends GoldenContractSuite (Wave 6 auto-discovery) from tech-companion-harness.
 * Uses Testcontainers Redis (idempotency); BFF has no PostgreSQL.
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
@ActiveProfiles("test")
@ExtendWith(DockerOrExternalPostgresCondition.class)
public class GoldenContractIT extends GoldenContractSuite {

    private static final ExperienceBffTestRedisSupport REDIS = ExperienceBffTestRedisSupport.fromEnvironment();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        REDIS.configure(registry);
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
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
