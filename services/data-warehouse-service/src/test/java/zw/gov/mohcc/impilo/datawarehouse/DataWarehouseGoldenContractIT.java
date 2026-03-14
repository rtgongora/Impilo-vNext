package zw.gov.mohcc.impilo.datawarehouse;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class DataWarehouseGoldenContractIT extends GoldenContractSuite {

    @Override
    protected String getReadEndpointOverride() {
        return "/external/v1/gold/datasets";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/gold/materialize";
    }
}
