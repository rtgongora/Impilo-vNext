package zw.gov.mohcc.impilo.notification;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;
import zw.gov.mohcc.impilo.notification.config.TestSecurityConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
public class NotificationGoldenContractTest extends GoldenContractSuite {

    @Override
    protected String getFederationEndpointOverride() {
        return "/internal/v1/test-federation";
    }
}
