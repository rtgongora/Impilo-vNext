package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncounterFormsControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static EncounterFormsController controller(PctServiceClient pct) {
        return new EncounterFormsController(pct);
    }

    @Test
    void resolve_forwardsEncounterId_andReturnsDataMeta() {
        StubPctClient pct = new StubPctClient();
        ResponseEntity<Map<String, Object>> response =
                controller(pct).resolve("42", Map.of("cadre", "NURSE"), "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertNotNull(data);
        assertTrue(data.has("mandatory"));
        Map<?, ?> meta = (Map<?, ?>) response.getBody().get("meta");
        assertEquals("req-1", meta.get("request_id"));
        assertEquals("42", meta.get("encounter_id"));
        assertEquals(42L, pct.lastResolveBody.get("encounterId")); // encounterId injected as numeric
        assertEquals("NURSE", pct.lastResolveBody.get("cadre"));
    }

    @Test
    void createDraft_buildsFormKeyBodyAndReturns() {
        StubPctClient pct = new StubPctClient();
        ResponseEntity<Map<String, Object>> response = controller(pct).createDraft(
                "42", "impilo.opd.triage.v1", Map.of("answers", Map.of("triageCategory", "3")), "req-2", "corr-2");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("impilo.opd.triage.v1", pct.lastCreateBody.get("formKey"));
        assertEquals(42L, pct.lastCreateBody.get("encounterId"));
    }

    @Test
    void submit_proxiesToPct() {
        StubPctClient pct = new StubPctClient();
        ResponseEntity<Map<String, Object>> response =
                controller(pct).submit("resp-9", null, "req-3", "corr-3");
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("SUBMITTED", data.get("status").asText());
    }

    @Test
    void listForEncounter_returnsDataMeta() {
        ResponseEntity<Map<String, Object>> response =
                controller(new StubPctClient()).listForEncounter("42", "req-4", "corr-4");
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> meta = (Map<?, ?>) response.getBody().get("meta");
        assertEquals("42", meta.get("encounter_id"));
    }

    @Test
    void listExtractionsForEncounter_proxiesProvenanceRows() {
        StubPctClient pct = new StubPctClient();
        ResponseEntity<Map<String, Object>> response =
                controller(pct).listExtractionsForEncounter("42", "req-5", "corr-5");
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertTrue(data.isArray());
        assertEquals("MEDICATION_REQUEST", data.get(0).get("resourceType").asText());
        assertEquals("ROUTED", data.get(0).get("status").asText());
        Map<?, ?> meta = (Map<?, ?>) response.getBody().get("meta");
        assertEquals("42", meta.get("encounter_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubPctClient extends PctServiceClient {
        Map<String, Object> lastResolveBody = new LinkedHashMap<>();
        Map<String, Object> lastCreateBody = new LinkedHashMap<>();

        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode resolveEncounterForms(Map<String, Object> body) {
            lastResolveBody = body;
            com.fasterxml.jackson.databind.node.ObjectNode node = mapper.createObjectNode();
            node.putArray("mandatory").addObject().put("formKey", "impilo.opd.triage.v1");
            return node;
        }

        @Override public JsonNode createFormResponse(Map<String, Object> body) {
            lastCreateBody = body;
            return mapper.createObjectNode().put("responseId", "resp-9").put("status", "DRAFT");
        }

        @Override public JsonNode submitFormResponse(String responseId, Map<String, Object> body) {
            return mapper.createObjectNode().put("responseId", responseId).put("status", "SUBMITTED");
        }

        @Override public JsonNode listEncounterFormResponses(String encounterId) {
            return mapper.createArrayNode();
        }

        @Override public JsonNode listEncounterFormExtractions(String encounterId) {
            com.fasterxml.jackson.databind.node.ArrayNode arr = mapper.createArrayNode();
            arr.addObject()
                    .put("extractedId", "x-1")
                    .put("resourceType", "MEDICATION_REQUEST")
                    .put("routeTarget", "OROS")
                    .put("status", "ROUTED")
                    .put("externalRef", "ord-123");
            return arr;
        }
    }
}
