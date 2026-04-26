package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.DagsServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DagsControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listPolicies_returnsDataAndMeta() {
        DataAccessGovernanceController controller = new DataAccessGovernanceController(new StubDagsClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listPolicies("t1", "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void createPolicy_returns201() {
        DataAccessGovernanceController controller = new DataAccessGovernanceController(new StubDagsClient());
        ResponseEntity<Map<String, Object>> response =
                controller.createPolicy("t1", "req-2", "corr-2",
                        Map.of("name", "Patient Data", "resourceType", "PATIENT", "effect", "ALLOW"));
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listAccessRequests_returnsDataAndMeta() {
        DataAccessGovernanceController controller = new DataAccessGovernanceController(new StubDagsClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listAccessRequests("t1", "req-3", "corr-3", "PENDING", 0, 50);
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
                null, null, null, null, null, null, null, null, null, null,
                null
        );
    }

    private static final class StubDagsClient extends DagsServiceClient {
        StubDagsClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode listPolicies() {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "pol-1").put("name", "Patient Data"));
            return arr;
        }

        @Override public JsonNode createPolicy(Map<String, Object> request) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", "pol-new");
            node.put("name", request.get("name").toString());
            return node;
        }

        @Override public JsonNode listAccessRequests(String status, int page, int size) {
            return mapper.createArrayNode();
        }

        @Override public JsonNode submitAccessRequest(Map<String, Object> request) {
            return mapper.createObjectNode().put("id", "ar-1");
        }
    }
}
