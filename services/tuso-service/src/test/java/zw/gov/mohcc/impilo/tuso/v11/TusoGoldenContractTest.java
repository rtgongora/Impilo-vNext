package zw.gov.mohcc.impilo.tuso.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

/**
 * Golden Contract integration test for TUSO v1.1 compliance.
 *
 * Read and command endpoints are auto-discovered from TusoV11ProbeController.
 * Federation endpoint is explicitly specified (cannot be auto-detected).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class TusoGoldenContractTest extends GoldenContractSuite {

    @Override
    protected String getFederationEndpointOverride() {
        return "/internal/v1/test-federation";
    }
}
