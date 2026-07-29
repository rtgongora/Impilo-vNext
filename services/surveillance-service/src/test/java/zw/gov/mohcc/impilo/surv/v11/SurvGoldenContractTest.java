package zw.gov.mohcc.impilo.surv.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;
import zw.gov.mohcc.impilo.surv.config.TestSecurityConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public class SurvGoldenContractTest extends GoldenContractSuite {

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
