package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiGovernanceControllerTest {

    @Test
    void listRulesFailClosesWhenDataGovernanceUnavailable() {
        AiGovernanceController controller = new AiGovernanceController(
                new ThrowingRestTemplate(),
                ServiceClientConfig.testServiceEndpoints());

        var response = controller.listRules("req-ai-1", "corr-ai-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("AI_GOVERNANCE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals("corr-ai-1", ((Map<?, ?>) response.getBody().get("meta")).get("correlation_id"));
    }

    @Test
    void incidentsReturnsExplicitNotImplementedUntilWired() {
        AiGovernanceController controller = new AiGovernanceController(
                new ThrowingRestTemplate(),
                ServiceClientConfig.testServiceEndpoints());

        var response = controller.listIncidents("tenant-1", "req-ai-2", "corr-ai-2");

        assertEquals(501, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("AI_GOVERNANCE_ROUTE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class ThrowingRestTemplate extends RestTemplate {
        @Override
        public <T> org.springframework.http.ResponseEntity<T> getForEntity(String url, Class<T> responseType, Object... uriVariables) {
            throw new RuntimeException("upstream unavailable");
        }
    }
}
