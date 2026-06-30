package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Theatre BFF composition tests (WS#6 theatre seam). The BFF must wrap the sovereign-service response
 * in {data, meta} and forward to inpatient-service — it persists nothing and fabricates nothing.
 */
class TheatreControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private TheatreController controller() {
        return new TheatreController(new StubInpatientClient());
    }

    @Test
    void queue_wrapsDataAndMeta() {
        ResponseEntity<Map<String, Object>> r = controller().queue("req-1", "corr-1");
        assertEquals(200, r.getStatusCode().value());
        assertEquals("req-1", ((Map<?, ?>) r.getBody().get("meta")).get("request_id"));
        assertTrue(r.getBody().get("data") instanceof ArrayNode);
    }

    @Test
    void intake_returns201WithCase() {
        ResponseEntity<Map<String, Object>> r = controller().intake("req-2", "corr-2",
                Map.of("patientId", "CPID-1", "procedureName", "Appendectomy"));
        assertEquals(201, r.getStatusCode().value());
        assertEquals("c1", ((JsonNode) r.getBody().get("data")).get("id").asText());
    }

    @Test
    void readiness_passesThroughBlockers() {
        ResponseEntity<Map<String, Object>> r = controller().evaluateReadiness("c1", "req-3", "corr-3", Map.of());
        JsonNode data = (JsonNode) r.getBody().get("data");
        assertFalse(data.get("bookable").asBoolean());
        assertTrue(data.get("blockers").size() > 0);
    }

    @Test
    void signNote_passesThrough() {
        ResponseEntity<Map<String, Object>> r = controller().signNote("c1", "req-4", "corr-4",
                Map.of("signedProviderId", "surgeon-1"));
        assertEquals("SIGNED", ((JsonNode) r.getBody().get("data")).get("status").asText());
    }

    @Test
    void death_routesAndReportsOwnerStatus() {
        ResponseEntity<Map<String, Object>> r = controller().death("c1", "req-5", "corr-5", Map.of());
        JsonNode data = (JsonNode) r.getBody().get("data");
        assertEquals("DECEASED", data.get("status").asText());
    }

    @Test
    void cancel_passesReason() {
        ResponseEntity<Map<String, Object>> r = controller().cancel("c1", "req-6", "corr-6",
                Map.of("reason", "Patient not fasted"));
        assertEquals("CANCELLED", ((JsonNode) r.getBody().get("data")).get("status").asText());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubInpatientClient extends InpatientServiceClient {
        StubInpatientClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode theatreQueue() {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "c1").put("triage_priority", "URGENT"));
            return arr;
        }

        @Override public JsonNode intakeTheatreCase(Map<String, Object> body) {
            return mapper.createObjectNode().put("id", "c1").put("procedure_name", "Appendectomy");
        }

        @Override public JsonNode evaluateTheatreReadiness(String caseId, Map<String, Object> body) {
            ObjectNode o = mapper.createObjectNode();
            o.put("bookable", false);
            ArrayNode blockers = o.putArray("blockers");
            blockers.add(mapper.createObjectNode().put("code", "NO_ROOM"));
            return o;
        }

        @Override public JsonNode signTheatreNote(String caseId, Map<String, Object> body) {
            return mapper.createObjectNode().put("status", "SIGNED").put("signed_provider_id", "surgeon-1");
        }

        @Override public JsonNode routeTheatreDeath(String caseId, Map<String, Object> body) {
            return mapper.createObjectNode().put("status", "DECEASED").put("death_routed", false);
        }

        @Override public JsonNode cancelTheatreCase(String caseId, Map<String, Object> body) {
            return mapper.createObjectNode().put("status", "CANCELLED");
        }
    }
}
