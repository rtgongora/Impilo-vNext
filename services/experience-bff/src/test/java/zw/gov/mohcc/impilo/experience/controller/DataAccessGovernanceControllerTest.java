package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.DagsServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DataAccessGovernanceControllerTest {

    @Test
    void listPoliciesReturnsBadGatewayWhenDagsUnavailable() {
        DataAccessGovernanceController controller = new DataAccessGovernanceController(new FailingDagsClient());

        var response = controller.listPolicies("tenant-1", "req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("DAGS_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class FailingDagsClient extends DagsServiceClient {
        private FailingDagsClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listPolicies() {
            throw new RuntimeException("dags unavailable");
        }
    }
}
