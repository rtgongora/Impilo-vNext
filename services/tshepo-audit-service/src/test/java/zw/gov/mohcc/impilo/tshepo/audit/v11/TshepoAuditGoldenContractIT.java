package zw.gov.mohcc.impilo.tshepo.audit.v11;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for TSHEPO Audit v1.1 compliance.
 *
 * Auto-discovers v1.1 endpoints via RequestMappingHandlerMapping.
 * Tests SKIP gracefully if no v1.1 endpoints are found.
 */
/**
 * MANUAL PostgreSQL integration test (keeps *IT; no maven-failsafe binding, so
 * not run by the surefire *Test pass). Cannot run under the H2 unit profile: an
 * audit entity uses a TIMESTAMPTZ columnDefinition that H2 cannot resolve as a
 * column type even via a compatibility DOMAIN. Run manually against real PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "it.pg.url", matches = ".+")
public class TshepoAuditGoldenContractIT extends GoldenContractSuite {

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getProperty("it.pg.url"));
        registry.add("spring.datasource.username", () -> System.getProperty("it.pg.user"));
        registry.add("spring.datasource.password", () -> System.getProperty("it.pg.pass"));
    }

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/health";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/test-command";
    }
    // All tests inherited from GoldenContractSuite.
    // Endpoints are auto-discovered at test time.
}
