package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConditionsControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listConditions_returnsDataAndMeta() {
        ConditionsController controller = new ConditionsController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listConditions("t1", "req-1", "corr-1", 0, 20, "patient-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void createCondition_returns201() {
        ConditionsController controller = new ConditionsController(new StubPctClient());
        ConditionsController.CreateConditionRequest request =
                new ConditionsController.CreateConditionRequest(
                        "patient-1", "enc-1", "Hypertension", "I10",
                        "PROBLEM_LIST", "ACTIVE", "MODERATE", null, "doc-1", "Notes");
        ResponseEntity<Map<String, Object>> response =
                controller.createCondition("t1", "pod-1", "req-2", "corr-2", null, request);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void resolveCondition_returns200() {
        ConditionsController controller = new ConditionsController(new StubPctClient());
        UUID condId = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response =
                controller.resolveCondition(condId, "t1", "pod-1", "req-3", "corr-3", null);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-3", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode listConditions(String patientCpid, int page, int size) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "cond-1").put("status", "ACTIVE"));
            return arr;
        }

        @Override public JsonNode createCondition(Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", "cond-new");
            node.put("condition_name", body.get("condition_name").toString());
            return node;
        }

        @Override public JsonNode resolveCondition(String conditionId) {
            return mapper.createObjectNode().put("id", conditionId).put("status", "RESOLVED");
        }
    }
}
