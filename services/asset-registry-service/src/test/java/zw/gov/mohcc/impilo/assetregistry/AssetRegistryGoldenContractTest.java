package zw.gov.mohcc.impilo.assetregistry;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class AssetRegistryGoldenContractTest extends GoldenContractSuite {

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/assets";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/assets/" + java.util.UUID.randomUUID();
    }
}
