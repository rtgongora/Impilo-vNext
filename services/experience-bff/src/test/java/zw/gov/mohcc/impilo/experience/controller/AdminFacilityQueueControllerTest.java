package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminFacilityQueueControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID FACILITY = UUID.randomUUID();

    @Test
    void materializationStatusProxiesPct() {
        var controller = new AdminFacilityQueueController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.materializationStatus(FACILITY, "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("MATERIALIZED", data.path("overall").asText());
    }

    @Test
    void queuesProxiesPct() {
        var controller = new AdminFacilityQueueController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response = controller.queues(FACILITY, "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertTrue(data.isArray());
    }

    @Test
    void reconcileProxiesPct() {
        var controller = new AdminFacilityQueueController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response = controller.reconcile(FACILITY, "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("OK", data.path("status").asText());
        assertEquals(2, data.path("created").asInt());
    }

    @Test
    void tusoUnreachableDuringReconcileSurfacesError() {
        var controller = new AdminFacilityQueueController(new ConflictPctClient());
        ResponseEntity<Map<String, Object>> response = controller.reconcile(FACILITY, "req-1", "corr-1");
        assertEquals(409, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("error"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static JsonNode node(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), MAPPER); }

        @Override
        public JsonNode getQueueMaterializationStatus(UUID facilityId) {
            return node("{\"facilityId\":\"" + facilityId + "\",\"overall\":\"MATERIALIZED\","
                    + "\"materialisedFromTuso\":2,\"seedDemoOnly\":0}");
        }

        @Override
        public JsonNode listQueues(UUID facilityId, UUID workspaceId) {
            return node("[{\"queueId\":\"q1\",\"source\":\"TUSO_MATERIALISED\","
                    + "\"materializationStatus\":\"MATERIALIZED\"}]");
        }

        @Override
        public JsonNode reconcileQueues(UUID facilityId) {
            return node("{\"facilityId\":\"" + facilityId + "\",\"status\":\"OK\",\"created\":2,"
                    + "\"updated\":0,\"retired\":0}");
        }
    }

    private static final class ConflictPctClient extends StubPctClient {
        @Override
        public JsonNode reconcileQueues(UUID facilityId) {
            throw HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict",
                    HttpHeaders.EMPTY, new byte[0], null);
        }
    }
}
