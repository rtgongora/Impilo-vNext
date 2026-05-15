package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

class MobileTelemedicineControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listSessionsRequiresPatientOrFacilityId() {
        MobileTelemedicineController controller = new MobileTelemedicineController(new StubPctClient());

        ResponseEntity<Map<String, Object>> response = controller.listSessions(
                "tenant-1", "req-1", "corr-1", null, null, null, null, null, 0, 20);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("MISSING_FILTER", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void createSessionRejectsInvalidScheduledAt() {
        MobileTelemedicineController controller = new MobileTelemedicineController(new StubPctClient());

        ResponseEntity<Map<String, Object>> response = controller.createSession(
                "tenant-1",
                "pod-1",
                "req-2",
                "corr-2",
                "idem-1",
                Map.of("patient_id", "CPID-1", "provider_id", "prov-1", "scheduled_at", "not-a-datetime"));

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INVALID_SCHEDULED_AT", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void createSessionReturnsCreatedWithMeta() {
        MobileTelemedicineController controller = new MobileTelemedicineController(new StubPctClient());

        ResponseEntity<Map<String, Object>> response = controller.createSession(
                "tenant-1",
                "pod-1",
                "req-3",
                "corr-3",
                "idem-2",
                Map.of("patient_id", "CPID-1", "provider_id", "prov-1"));

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("tele-1", ((JsonNode) response.getBody().get("data")).get("id").asText());
        assertEquals("req-3", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void joinSessionReturnsBadGatewayWhenPctFails() {
        MobileTelemedicineController controller = new MobileTelemedicineController(new FailingPctClient());

        ResponseEntity<Map<String, Object>> response = controller.joinSession(
                UUID.randomUUID(), "tenant-1", "pod-1", "req-4", "corr-4", null, Map.of());

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PCT_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void listSessionsReturnsBadGatewayWhenPctUnavailable() {
        MobileTelemedicineController controller = new MobileTelemedicineController(new FailingPctClient());

        ResponseEntity<Map<String, Object>> response = controller.listSessions(
                "tenant-1", "req-5", "corr-5", null, "cpid-1", null, null, null, 0, 20);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PCT_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubPctClient extends PctServiceClient {
        private StubPctClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public JsonNode requestTelehealthSession(Map<String, Object> request) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", "tele-1");
            node.put("status", "SCHEDULED");
            return node;
        }
    }

    private static final class FailingPctClient extends PctServiceClient {
        private FailingPctClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public JsonNode getPatientTelehealthSessions(String cpid, String status, int page, int size) {
            throw new RuntimeException("pct unavailable");
        }

        @Override
        public JsonNode joinTelehealthSession(String sessionId) {
            throw new RuntimeException("pct unavailable");
        }
    }
}
