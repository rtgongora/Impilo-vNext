package zw.gov.mohcc.impilo.vito.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for VITO v1.1 compliance.
 *
 * Read and command endpoints are auto-discovered from VitoV11ProbeController.
 * Federation endpoint is explicitly specified (cannot be auto-detected).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class VitoGoldenContractIT extends GoldenContractSuite {

    @Override
    protected String getFederationEndpointOverride() {
        return "/internal/v1/test-federation";
    }
}
