package zw.gov.mohcc.impilo.schemaregistry;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class SchemaRegistryGoldenContractIT extends GoldenContractSuite {
    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/schemas/subjects";
    }
    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/schemas";
    }
}
