package zw.gov.mohcc.impilo.tshepo.keys.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for TSHEPO Keys v1.1 compliance.
 *
 * Auto-discovers v1.1 endpoints via RequestMappingHandlerMapping.
 * Tests SKIP gracefully if no v1.1 endpoints are found.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class TshepoKeysGoldenContractIT extends GoldenContractSuite {
    // All tests inherited from GoldenContractSuite.
    // Endpoints are auto-discovered at test time.
}
