package zw.gov.mohcc.impilo.experience.intelligence;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;

class HealthIntelligenceAuthorizationServiceTest {

    @Test
    void allowAnonymous_skipsAuthenticationAndTshepo() {
        TshepoAuthzServiceClient tshepo = Mockito.mock(TshepoAuthzServiceClient.class);
        HealthIntelligenceAuthorizationService svc = new HealthIntelligenceAuthorizationService(tshepo);
        ReflectionTestUtils.setField(svc, "allowAnonymous", true);
        ReflectionTestUtils.setField(svc, "requireTshepoAuthorize", true);

        svc.assertIntelligencePlaneAccess("TEST");

        Mockito.verifyNoInteractions(tshepo);
    }
}
