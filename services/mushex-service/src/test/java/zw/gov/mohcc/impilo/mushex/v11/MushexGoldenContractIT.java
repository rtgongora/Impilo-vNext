package zw.gov.mohcc.impilo.mushex.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for MUSHEX v1.1 compliance.
 *
 * Auto-discovers v1.1 endpoints via RequestMappingHandlerMapping.
 * Tests SKIP gracefully if no v1.1 endpoints are found.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class MushexGoldenContractIT extends GoldenContractSuite {
    @Override
    protected String getCommandEndpointOverride() {
        // Use a concrete internal command path (no URI template segments). The companion
        // idempotency filter is asserted at filter level; handler mapping is not required.
        return "/internal/v1/contract-probe/command";
    }

    @Override
    protected boolean supportsClientTimeoutOnRead() {
        // MusheX does not currently expose a read path that reliably returns companion 504
        // under MockMvc for X-Client-Timeout-MS=0.
        return false;
    }
}
