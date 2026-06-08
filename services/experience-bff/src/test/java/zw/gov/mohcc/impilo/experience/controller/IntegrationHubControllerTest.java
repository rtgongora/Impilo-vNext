package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.IntegrationHubServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationHubControllerTest {

    @Test
    void replayDeadLetterReturnsDataWhenHubSucceeds() {
        IntegrationHubController controller = new IntegrationHubController(new SuccessIntegrationHubClient());
        var response = controller.replayDeadLetter("dl-1", "tenant-1", "req-replay", "corr-replay");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
        assertTrue(response.getBody().get("data").toString().contains("REPLAYED"));
    }

    @Test
    void listMappingTemplatesReturnsDataWhenHubSucceeds() {
        IntegrationHubController controller = new IntegrationHubController(new SuccessIntegrationHubClient());
        var response = controller.listMappingTemplates("tenant-1", "req-tpl", "corr-tpl");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void listRoutesReturnsBadGatewayWhenIntegrationHubUnavailable() {
        IntegrationHubController controller = new IntegrationHubController(new FailingIntegrationHubClient());

        var response = controller.listRoutes("tenant-1", "req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INTEGRATION_HUB_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final class SuccessIntegrationHubClient extends IntegrationHubServiceClient {
        private SuccessIntegrationHubClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode replayDeadLetter(String id) {
            return MAPPER.createObjectNode().put("id", id).put("status", "REPLAYED");
        }

        @Override
        public JsonNode listMappingTemplates() {
            ArrayNode arr = MAPPER.createArrayNode();
            arr.add(MAPPER.createObjectNode().put("name", "FHIR_PATIENT"));
            return arr;
        }
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
