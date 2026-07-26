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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the BFF interpretation proxy: success passes the upstream payload through, and an
 * upstream failure returns 502 rather than an empty interpretation that would read as a completed
 * review which flagged nothing.
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
    /**
     * This asserted a 200 with an empty interpretation and called it "failing honest". An empty
     * interpretation is what a completed review that flagged nothing looks like, so the caller
     * could not tell a clean result from an engine that never ran. Honest now means 502.
     */
    void interpretationEvaluate_failsHonestOnUpstreamError() {
        ClinicalKnowledgeController controller = new ClinicalKnowledgeController(new StubClient(true));
        ResponseEntity<Map<String, Object>> response = controller.interpretationEvaluate("t1", Map.of());

        assertEquals(502, response.getStatusCode().value());
        assertEquals("interpretation_unavailable", response.getBody().get("error"));
        assertNull(response.getBody().get("data"),
                "an empty interpretation here would read as 'reviewed, nothing abnormal'");
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
