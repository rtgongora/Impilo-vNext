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

    // ── Perioperative depth passthrough (UI-completion) ──

    @Test
    void caseDetail_wrapsEnrichedEpisode() {
        ResponseEntity<Map<String, Object>> r = controller().caseDetail("c1", "req-d", "corr-d");
        assertEquals(200, r.getStatusCode().value());
        JsonNode data = (JsonNode) r.getBody().get("data");
        assertEquals("c1", data.get("id").asText());
        assertEquals("EMERGENCY", data.get("triage_priority").asText());
    }

    @Test
    void bloodAction_forwardsActionSegment_and201() {
        ResponseEntity<Map<String, Object>> r = controller().bloodAction("c1", "request", "req-b", "corr-b",
                Map.of("units", 2, "componentType", "PRBC"));
        assertEquals(201, r.getStatusCode().value());
        assertEquals("request", ((JsonNode) r.getBody().get("data")).get("action").asText());
    }

    @Test
    void listBlood_failsSoftToEmptyList() {
        ResponseEntity<Map<String, Object>> r = controller().listBlood("boom", "req-b2", "corr-b2");
        assertEquals(200, r.getStatusCode().value());
        assertTrue(((java.util.List<?>) r.getBody().get("data")).isEmpty());
    }

    @Test
    void recordCount_wrapsDiscrepancyGate() {
        ResponseEntity<Map<String, Object>> r = controller().recordCount("c1", "req-c", "corr-c",
                Map.of("kind", "SWAB", "baselineCount", 10, "closingCount", 9));
        assertEquals(201, r.getStatusCode().value());
        assertEquals(1, ((JsonNode) r.getBody().get("data")).get("discrepancy").asInt());
    }

    @Test
    void activateEmergency_returns201WithOverride() {
        ResponseEntity<Map<String, Object>> r = controller().activateEmergency("req-e", "corr-e",
                Map.of("patientId", "CPID-9", "procedureName", "Laparotomy"));
        assertEquals(201, r.getStatusCode().value());
        assertTrue(((JsonNode) r.getBody().get("data")).get("emergency_override").asBoolean());
    }

    @Test
    void obstetricAction_forwardsActionSegment() {
        ResponseEntity<Map<String, Object>> r = controller().obstetricAction("c1", "delivery", "req-o", "corr-o",
                Map.of("babyCpid", "CPID-BABY-1"));
        assertEquals(201, r.getStatusCode().value());
        assertEquals("delivery", ((JsonNode) r.getBody().get("data")).get("action").asText());
    }

    @Test
    void recordImplant_wrapsUdi() {
        ResponseEntity<Map<String, Object>> r = controller().recordImplant("c1", "req-i", "corr-i",
                Map.of("udi", "UDI-123", "bodySite", "L_HIP"));
        assertEquals(201, r.getStatusCode().value());
        assertEquals("UDI-123", ((JsonNode) r.getBody().get("data")).get("udi").asText());
    }

    @Test
    void recordControlledDrug_wrapsWitness() {
        ResponseEntity<Map<String, Object>> r = controller().recordControlledDrug("c1", "req-cd", "corr-cd",
                Map.of("action", "ADMINISTER", "itemCode", "MORPH-10", "witnessProvider", "nurse-2"));
        assertEquals(201, r.getStatusCode().value());
        assertEquals("nurse-2", ((JsonNode) r.getBody().get("data")).get("witness_provider").asText());
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

        @Override public JsonNode getTheatreCase(String caseId) {
            return mapper.createObjectNode().put("id", caseId).put("triage_priority", "EMERGENCY")
                    .put("emergency_override", true);
        }

        @Override public JsonNode listTheatreBlood(String caseId) {
            if ("boom".equals(caseId)) { throw new RuntimeException("upstream down"); }
            return mapper.createArrayNode();
        }

        @Override public JsonNode theatreBloodAction(String caseId, String action, Map<String, Object> body) {
            return mapper.createObjectNode().put("action", action).put("status", "GROUP_SCREEN");
        }

        @Override public JsonNode recordTheatreCount(String caseId, Map<String, Object> body) {
            return mapper.createObjectNode().put("kind", "SWAB").put("discrepancy", 1).put("reconciled", false);
        }

        @Override public JsonNode activateEmergencyCase(Map<String, Object> body) {
            return mapper.createObjectNode().put("id", "c-emg").put("emergency_override", true)
                    .put("triage_priority", "EMERGENCY");
        }

        @Override public JsonNode theatreObstetricAction(String caseId, String action, Map<String, Object> body) {
            return mapper.createObjectNode().put("action", action).put("episode_id", caseId);
        }

        @Override public JsonNode recordTheatreImplant(String caseId, Map<String, Object> body) {
            return mapper.createObjectNode().put("udi", "UDI-123").put("status", "IMPLANTED");
        }

        @Override public JsonNode recordControlledDrug(String caseId, Map<String, Object> body) {
            return mapper.createObjectNode().put("witness_provider", "nurse-2").put("status", "RECORDED");
        }
    }
}
