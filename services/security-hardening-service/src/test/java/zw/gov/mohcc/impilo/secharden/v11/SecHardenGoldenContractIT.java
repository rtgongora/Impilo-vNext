package zw.gov.mohcc.impilo.secharden.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;
import zw.gov.mohcc.impilo.secharden.config.TestSecurityConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public class SecHardenGoldenContractIT extends GoldenContractSuite {

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/policy-packs";
    }

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/scans";
    }

    @Override
    protected String getFederationEndpointOverride() {
        return "/internal/v1/policy-packs";
    }
}
