package zw.gov.mohcc.impilo.shareslip.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("test")
public class ShareSlipGoldenContractIT extends GoldenContractSuite {
    // All tests inherited from GoldenContractSuite.
    // Endpoints are auto-discovered at test time.
}
