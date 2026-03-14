package zw.gov.mohcc.impilo.workflow;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class WorkflowGoldenContractIT extends GoldenContractSuite {
    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/workflows/definitions";
    }
    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/workflows/definitions";
    }
}
