package zw.gov.mohcc.impilo.obs.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;
import zw.gov.mohcc.impilo.obs.config.TestSecurityConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public class ObsGoldenContractIT extends GoldenContractSuite {

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/dashboards";
    }

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/alert-rules";
    }

    @Override
    protected String getFederationEndpointOverride() {
        return "/internal/v1/dashboards";
    }
}
