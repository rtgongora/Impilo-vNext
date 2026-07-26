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

import static org.junit.jupiter.api.Assertions.*;

/**
 * The partograph and CTG proxies.
 *
 * <p>These routes were a 404 between {@code 062d827c9} and the restoration of the two controllers:
 * PCT had the endpoints, the client had the methods, the shell was calling the paths, and nothing
 * mapped the route in between. These tests hold the route open and, more importantly, hold the
 * failure semantics: a labour chart that cannot be read must say so, because an empty partograph and
 * an unreachable one are different statements about a woman in labour.
 */
class MaternityMonitoringProxyTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ── partograph ──────────────────────────────────────────────────────────

    @Test
    void activePartograph_wrapsDataAndMeta() {
        ResponseEntity<Map<String, Object>> r =
                new MaternityPartographController(new StubPctClient()).activeSession("p-1", "enc-1", "req-1", "corr-1");
        assertEquals(200, r.getStatusCode().value());
        assertEquals("req-1", ((Map<?, ?>) r.getBody().get("meta")).get("request_id"));
        assertEquals("p-1", ((Map<?, ?>) r.getBody().get("meta")).get("patient_id"));
        assertEquals("ps-1", ((JsonNode) r.getBody().get("data")).get("session_id").asText());
    }

    @Test
    void noOpenPartographIsAnAnswer_notAFailure() {
        // PCT answers "there is no open partograph" explicitly. That is a real clinical finding and
        // must pass through as 200 — only an inability to ask is a 502.
        StubPctClient stub = new StubPctClient();
        stub.activePartographPayload = mapper.createObjectNode()
                .put("patient_id", "p-1").put("partograph_active", false);
        ResponseEntity<Map<String, Object>> r =
                new MaternityPartographController(stub).activeSession("p-1", null, "req-1", "corr-1");
        assertEquals(200, r.getStatusCode().value());
        assertFalse(((JsonNode) r.getBody().get("data")).get("partograph_active").asBoolean());
    }

    @Test
    void addPoint_passesThroughTheProgressAssessmentWithTheWrite() {
        // PCT returns the assessment alongside the written point on purpose: a reading that crosses
        // the action line has to reach the midwife who just recorded it.
        ResponseEntity<Map<String, Object>> r = new MaternityPartographController(new StubPctClient())
                .addPoint("ps-1", Map.of("cervicalDilationCm", 5), "req-2", "corr-2");
        assertEquals(200, r.getStatusCode().value());
        assertEquals("AT_OR_RIGHT_OF_ACTION",
                ((JsonNode) r.getBody().get("data")).get("assessment").get("status").asText());
    }

    @Test
    void closeAcceptsBothVerbsAndBothReachPct() {
        // The shell sends PATCH; PCT exposes POST. Accepting one only would leave a session that
        // reads as closed in the browser and is still open in the record.
        StubPctClient stub = new StubPctClient();
        MaternityPartographController controller = new MaternityPartographController(stub);

        assertEquals(200, controller.closeSession("ps-1", Map.of("outcome", "DELIVERED"), "r", "c")
                .getStatusCode().value());
        assertEquals(1, stub.closeCalls);

        // A close with no body is still a close.
        assertEquals(200, controller.closeSession("ps-1", null, "r", "c").getStatusCode().value());
        assertEquals(2, stub.closeCalls);
    }

    @Test
    void unreachablePartograph_is502AndSaysNotToReadItAsAbsence() {
        StubPctClient stub = new StubPctClient();
        stub.blowUp = true;
        ResponseEntity<Map<String, Object>> r =
                new MaternityPartographController(stub).activeSession("p-1", null, "req-3", "corr-3");
        assertEquals(502, r.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) r.getBody().get("error");
        assertEquals("PCT_UNAVAILABLE", error.get("code"));
        assertTrue(String.valueOf(error.get("clinical_note")).contains("absence of labour observations"));
    }

    @Test
    void nullPayloadIsAlsoAFailure_notAnEmptyChart() {
        StubPctClient stub = new StubPctClient();
        stub.returnNull = true;
        ResponseEntity<Map<String, Object>> r =
                new MaternityPartographController(stub).getSession("ps-1", "req-4", "corr-4");
        assertEquals(502, r.getStatusCode().value());
    }

    // ── CTG ─────────────────────────────────────────────────────────────────

    @Test
    void ctgChunks_wrapDataAndMeta() {
        ResponseEntity<Map<String, Object>> r = new FetalMonitoringController(new StubPctClient())
                .getChunks("cs-1", "FHR", null, null, "req-5", "corr-5");
        assertEquals(200, r.getStatusCode().value());
        assertTrue(r.getBody().get("data") instanceof ArrayNode);
    }

    @Test
    void ctgChunkCarriesTheGapItCouldNotMeasure() {
        // A chunk that declares more samples than it holds records the shortfall rather than
        // interpolating; a flat line the transducer never measured looks identical on screen to one
        // it did. The proxy must not smooth that away.
        ResponseEntity<Map<String, Object>> r = new FetalMonitoringController(new StubPctClient())
                .addChunk("cs-1", Map.of("channel", "FHR"), "req-6", "corr-6");
        assertEquals(3, ((JsonNode) r.getBody().get("data")).get("missing_sample_count").asInt());
    }

    @Test
    void unreachableCtg_is502AndSaysNotToReadItAsAbsence() {
        StubPctClient stub = new StubPctClient();
        stub.blowUp = true;
        ResponseEntity<Map<String, Object>> r =
                new FetalMonitoringController(stub).activeSession("p-1", null, "req-7", "corr-7");
        assertEquals(502, r.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) r.getBody().get("error");
        assertTrue(String.valueOf(error.get("clinical_note")).contains("absence of monitoring"));
    }

    // ────────────────────────────────────────────────────────────────────────

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubPctClient extends PctServiceClient {
        boolean blowUp = false;
        boolean returnNull = false;
        int closeCalls = 0;
        JsonNode activePartographPayload = mapper.createObjectNode().put("session_id", "ps-1");

        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        private JsonNode answer(JsonNode payload) {
            if (blowUp) throw new IllegalStateException("pct unreachable");
            return returnNull ? null : payload;
        }

        @Override public JsonNode getActivePartographSession(String patientId, String encounterId) {
            return answer(activePartographPayload);
        }

        @Override public JsonNode getPartographSession(String sessionId) {
            return answer(mapper.createObjectNode().put("session_id", sessionId));
        }

        @Override public JsonNode createPartographSession(Map<String, Object> body) {
            return answer(mapper.createObjectNode().put("session_id", "ps-new"));
        }

        @Override public JsonNode addPartographPoint(String sessionId, Map<String, Object> body) {
            ObjectNode out = mapper.createObjectNode();
            out.put("observation_id", "obs-1");
            out.set("assessment", mapper.createObjectNode().put("status", "AT_OR_RIGHT_OF_ACTION"));
            return answer(out);
        }

        @Override public JsonNode closePartographSession(String sessionId, Map<String, Object> body) {
            closeCalls++;
            return answer(mapper.createObjectNode().put("session_id", sessionId).put("status", "CLOSED"));
        }

        @Override public JsonNode getActiveFetalMonitoringSession(String patientId, String encounterId) {
            return answer(mapper.createObjectNode().put("session_id", "cs-1"));
        }

        @Override public JsonNode getFetalMonitoringChunks(String sessionId, String channel, String from, String to) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("chunk_id", "ch-1"));
            return answer(arr);
        }

        @Override public JsonNode addFetalMonitoringChunk(String sessionId, Map<String, Object> body) {
            return answer(mapper.createObjectNode().put("chunk_id", "ch-new").put("missing_sample_count", 3));
        }
    }
}
