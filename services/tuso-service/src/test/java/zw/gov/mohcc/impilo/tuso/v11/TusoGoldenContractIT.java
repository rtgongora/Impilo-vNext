package zw.gov.mohcc.impilo.tuso.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for TUSO v1.1 compliance.
 *
 * Extends the reusable GoldenContractSuite from tech-companion-harness.
 * Verifies:
 *   - Header enforcement (missing tenant/pod/request-id/correlation-id → 400)
 *   - Error envelope format (all required fields present)
 *   - Idempotency (same key + same body → replay; different body → 409)
 *   - Federation authority (private pod → 403)
 *
 * Requires running Spring Boot context with CompanionV11Config and
 * TusoV11ProbeController active.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class TusoGoldenContractIT extends GoldenContractSuite {
    // Inherits all tests from GoldenContractSuite.
    // Default endpoint paths match TusoV11ProbeController:
    //   GET  /internal/v1/health
    //   POST /internal/v1/test-command
    //   POST /internal/v1/test-federation
}
