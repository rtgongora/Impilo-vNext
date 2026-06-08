package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.NhumeServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NhumeControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void dashboard_returnsDataAndMeta() {
        NhumeController controller = new NhumeController(new StubNhumeClient());
        ResponseEntity<Map<String, Object>> response = controller.dashboard("req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void getDelivery_returnsDataAndMeta() {
        NhumeController controller = new NhumeController(new StubNhumeClient());
        ResponseEntity<Map<String, Object>> response = controller.getDelivery("del-1", "req-2", "corr-2");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("data"));
        assertEquals("del-1", MAPPER.valueToTree(response.getBody().get("data")).get("delivery_id").asText());
    }

    private static final class StubNhumeClient extends NhumeServiceClient {
        StubNhumeClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode getDashboard() {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("tenant_id", "tenant-1");
            return node;
        }

        @Override
        public JsonNode getDelivery(String deliveryId) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("delivery_id", deliveryId);
            return node;
        }
    }
}
