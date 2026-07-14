package zw.gov.mohcc.impilo.tshepo.audit.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("test")
public class TshepoAuditGoldenContractIT extends GoldenContractSuite {
    // All tests inherited from GoldenContractSuite.
    // Endpoints are auto-discovered at test time.
}
