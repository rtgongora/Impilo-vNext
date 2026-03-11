package zw.gov.mohcc.impilo.dataingestion;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class DataIngestionGoldenContractIT extends GoldenContractSuite {

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/ingestion-jobs";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/ingestion-jobs/" + java.util.UUID.randomUUID();
    }
}
