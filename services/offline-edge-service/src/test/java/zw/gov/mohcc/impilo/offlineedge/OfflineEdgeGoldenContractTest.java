package zw.gov.mohcc.impilo.offlineedge;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class OfflineEdgeGoldenContractTest extends GoldenContractSuite {
    @Override
    protected String getReadEndpointOverride() {
        // A random entitlement id is 404 by construction; the header-enforcement
        // contract just needs a stable 2xx GET on a v1.1 path.
        return "/internal/v1/health";
    }
    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/offline/entitlements";
    }
}
