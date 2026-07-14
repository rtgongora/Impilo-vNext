package zw.gov.mohcc.impilo.rules;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class RulesGoldenContractIT extends GoldenContractSuite {

    @Override
    protected String getFederationEndpointOverride() {
        return "/internal/v1/rules";
    }
}
