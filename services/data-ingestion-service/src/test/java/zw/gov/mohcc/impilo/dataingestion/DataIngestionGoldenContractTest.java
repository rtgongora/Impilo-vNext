package zw.gov.mohcc.impilo.dataingestion;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class DataIngestionGoldenContractTest extends GoldenContractSuite {

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/ingest/health";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/ingest/events";
    }
}
