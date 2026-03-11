package zw.gov.mohcc.impilo.datagovernance;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class DataGovernanceGoldenContractIT extends GoldenContractSuite {

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/governance-rules";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/governance-rules/" + java.util.UUID.randomUUID();
    }
}
