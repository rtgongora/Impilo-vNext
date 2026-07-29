package zw.gov.mohcc.impilo.shareslip.v11;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for Share Slip v1.1 compliance.
 *
 * Auto-discovers v1.1 endpoints via RequestMappingHandlerMapping.
 * Tests SKIP gracefully if no v1.1 endpoints are found.
 */
/**
 * MANUAL PostgreSQL integration test (keeps the *IT suffix; no maven-failsafe
 * binding, so it is not run by the surefire *Test pass). It cannot run under the
 * H2 unit profile because share-slip entities use PostgreSQL-specific column DDL that H2 cannot create via ddl-auto. Run manually against a real PostgreSQL instance.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@EnabledIfSystemProperty(named = "it.pg.url", matches = ".+")
public class ShareSlipGoldenContractIT extends GoldenContractSuite {

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
}
