package zw.gov.mohcc.impilo.msika.v11;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for MSIKA v1.1 compliance.
 *
 * Auto-discovers v1.1 endpoints via RequestMappingHandlerMapping.
 * Tests SKIP gracefully if no v1.1 endpoints are found.
 */
// Revived from a dead *IT (no failsafe at HEAD). Needs a real Postgres on
// localhost:5432 — run with RUN_DB_CONTRACT_TESTS=1 against a scratch DB;
// skipped (visibly) otherwise so the default suite stays hermetic.
@EnabledIfEnvironmentVariable(named = "RUN_DB_CONTRACT_TESTS", matches = "1")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class MsikaGoldenContractTest extends GoldenContractSuite {
    // All tests inherited from GoldenContractSuite.
    // Endpoints are auto-discovered at test time.
}
