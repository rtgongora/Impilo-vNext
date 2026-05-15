package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MobileGovernanceControllerTest {

    @Test
    void summaryFailClosesWhenGovernanceUnavailable() {
        MobileGovernanceController controller = new MobileGovernanceController(
                new ThrowingRestTemplate(),
                ServiceClientConfig.testServiceEndpoints());

        var response = controller.getGovernanceSummary("tenant-1", "req-mg-1", "corr-mg-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("DATA_GOVERNANCE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void notificationsFailCloseWhenNotificationServiceUnavailable() {
        MobileGovernanceController controller = new MobileGovernanceController(
                new ThrowingRestTemplate(),
                ServiceClientConfig.testServiceEndpoints());

        var response = controller.getNotifications("req-mg-2", "corr-mg-2");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("NOTIFICATIONS_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class ThrowingRestTemplate extends RestTemplate {
        @Override
        public <T> org.springframework.http.ResponseEntity<T> getForEntity(String url, Class<T> responseType, Object... uriVariables) {
            throw new RuntimeException("service unavailable");
        }
    }
}
