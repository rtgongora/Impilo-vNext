package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the BFF interpretation proxy: success passes the upstream payload through, and an
 * upstream failure fails honest (empty interpretation, never fabricated flags).
 */
class ClinicalKnowledgeInterpretationControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void interpretationEvaluate_passesThroughUpstreamData() {
        ClinicalKnowledgeController controller = new ClinicalKnowledgeController(new StubClient(false));
        ResponseEntity<Map<String, Object>> response = controller.interpretationEvaluate("t1", Map.of());

        assertEquals(200, response.getStatusCode().value());
        Object data = response.getBody().get("data");
        assertNotNull(data);
        JsonNode node = MAPPER.valueToTree(data);
        assertEquals("CRITICAL_HIGH", node.get("interpreted_observations").get(0).get("interpretation").asText());
    }

    @Test
    void interpretationEvaluate_failsHonestOnUpstreamError() {
        ClinicalKnowledgeController controller = new ClinicalKnowledgeController(new StubClient(true));
        ResponseEntity<Map<String, Object>> response = controller.interpretationEvaluate("t1", Map.of());

        assertEquals(200, response.getStatusCode().value());
        JsonNode node = MAPPER.valueToTree(response.getBody().get("data"));
        assertTrue(node.get("interpreted_observations").isEmpty());
        assertTrue(node.get("alerts").isEmpty());
    }

    private static final class StubClient extends ClinicalKnowledgePlatformClient {
        private final boolean fail;

        StubClient(boolean fail) {
            super(new RestTemplate(), new ClinicalPlatformProperties("http://clinical"));
            this.fail = fail;
        }

        @Override
        public JsonNode interpretationEvaluate(Map<String, Object> body) {
            if (fail) {
                throw new RuntimeException("upstream down");
            }
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode obs = MAPPER.createObjectNode();
            obs.put("interpretation", "CRITICAL_HIGH");
            root.set("interpreted_observations", MAPPER.createArrayNode().add(obs));
            root.set("alerts", MAPPER.createArrayNode());
            root.set("ranges_used", MAPPER.createArrayNode());
            return root;
        }
    }
}
