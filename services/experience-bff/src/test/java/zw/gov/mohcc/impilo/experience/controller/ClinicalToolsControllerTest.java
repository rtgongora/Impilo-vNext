package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.TshepoOfflineServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClinicalToolsControllerTest {

    @Test
    void getOfflineQueue_returnsQueueDepthFromTshepoPack() {
        ClinicalToolsController controller = new ClinicalToolsController(
                new RestTemplate(),
                ServiceClientConfig.testServiceEndpoints(),
                new StubOfflineClient());

        ResponseEntity<Map<String, Object>> response = controller.getOfflineQueue(
                "9b2ec8af-5e6b-4016-a336-c7bc132614f5", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals(2, data.get("queue_depth"));
        assertEquals("tshepo-offline-pack", data.get("source"));
    }

    private static final class StubOfflineClient extends TshepoOfflineServiceClient {
        private static final ObjectMapper mapper = new ObjectMapper();

        StubOfflineClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode getLatestOfflinePackForFacility(UUID facilityId) {
            ArrayNode pack = mapper.createArrayNode();
            pack.add(mapper.createObjectNode().put("entity_id", "e1"));
            pack.add(mapper.createObjectNode().put("entity_id", "e2"));
            return pack;
        }
    }
}
