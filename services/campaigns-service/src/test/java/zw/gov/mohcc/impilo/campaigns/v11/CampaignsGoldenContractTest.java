package zw.gov.mohcc.impilo.campaigns.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.campaigns.config.TestSecurityConfig;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public class CampaignsGoldenContractTest extends GoldenContractSuite {

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/test-command";
    }

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/health";
    }

    @Override
    protected String getFederationEndpointOverride() {
        return "/internal/v1/test-federation";
    }
}
