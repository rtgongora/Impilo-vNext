package zw.gov.mohcc.impilo.ndr;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class NdrGoldenContractIT extends GoldenContractSuite {

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/ndr/query/bronze";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/ndr/ingest/events";
    }
}
