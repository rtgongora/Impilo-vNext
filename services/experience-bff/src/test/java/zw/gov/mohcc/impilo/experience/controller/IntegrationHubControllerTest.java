package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.IntegrationHubServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IntegrationHubControllerTest {

    @Test
    void listRoutesReturnsBadGatewayWhenIntegrationHubUnavailable() {
        IntegrationHubController controller = new IntegrationHubController(new FailingIntegrationHubClient());

        var response = controller.listRoutes("tenant-1", "req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INTEGRATION_HUB_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class FailingIntegrationHubClient extends IntegrationHubServiceClient {
        private FailingIntegrationHubClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode listRoutes() {
            throw new RuntimeException("integration hub unavailable");
        }
    }
}
