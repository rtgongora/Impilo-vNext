package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClinicalCurationControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void list_returnsReviewItemsAndStatus200() {
        ClinicalCurationController controller = new ClinicalCurationController(new StubClinicalClient());
        ResponseEntity<Map<String, Object>> response =
                controller.list("t1", "PROPOSED");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void decide_returnsDecisionData() {
        ClinicalCurationController controller = new ClinicalCurationController(new StubClinicalClient());
        UUID id = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response =
                controller.decide("t1", id, Map.of("decision", "APPROVE"));
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    private static final class StubClinicalClient extends ClinicalKnowledgePlatformClient {
        StubClinicalClient() {
            super(new RestTemplate(), new ClinicalPlatformProperties("http://clinical"));
        }

        @Override public JsonNode listKnowledgeReviewItems(String status) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "ri-1").put("status", status));
            return arr;
        }

        @Override public JsonNode decideKnowledgeReviewItem(UUID id, Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", id.toString());
            node.put("decision", body.get("decision").toString());
            return node;
        }
    }
}
