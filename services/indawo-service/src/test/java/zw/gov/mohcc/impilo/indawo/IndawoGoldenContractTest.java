package zw.gov.mohcc.impilo.indawo;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class IndawoGoldenContractTest extends GoldenContractSuite {

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/sites";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/sites/" + java.util.UUID.randomUUID();
    }
}
