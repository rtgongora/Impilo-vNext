package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The problem list, as the experience layer serves it.
 *
 * <p>These tests exist because the previous ones did not catch that this controller called an
 * endpoint pct-service has never served. They asserted status codes and echoed metadata against a
 * stub that answered whatever was asked of it, so they passed for as long as the vertical was
 * dead. What is asserted here instead is the translation itself: the field names PCT actually
 * uses, and the field names the UI actually reads.</p>
 */
class ConditionsControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ── The read path ───────────────────────────────────────────────────────────

    @Test
    void listMapsPctProblemOntoTheConditionResourceTheUiReads() {
        ConditionsController controller = new ConditionsController(new StubPctClient());

        ResponseEntity<Map<String, Object>> response =
                controller.listConditions("t1", "req-1", "corr-1", "patient-1");

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> attrs = firstAttributes(response);
        // The UI's ConditionResource reads conditionName; PCT stores it as display.
        assertEquals("Hypertension", attrs.get("conditionName"));
        assertEquals("patient-1", attrs.get("patientId"));
        assertEquals("ACTIVE", attrs.get("clinicalStatus"));
        assertEquals("MODERATE", attrs.get("severity"));
        assertEquals("prob-1", firstResource(response).get("id"));
        assertEquals("condition", firstResource(response).get("type"));
    }

    @Test
    void listSurfacesAnIcdCodeAsAnIcdCode() {
        ConditionsController controller = new ConditionsController(new StubPctClient());
        Map<String, Object> attrs = firstAttributes(
                controller.listConditions("t1", "req-1", "corr-1", "patient-1"));
        assertEquals("I10", attrs.get("code"));
        assertEquals("ICD-10", attrs.get("codeSystem"));
        assertEquals("I10", attrs.get("icdCode"));
    }

    /**
     * A SNOMED code rendered into a field labelled ICD is a mislabelled code, and a mislabelled
     * code is how a problem is counted into the wrong programme or the wrong claim. The code still
     * travels — under its own system.
     */
    @Test
    void listRefusesToPresentANonIcdCodeAsAnIcdCode() {
        StubPctClient stub = new StubPctClient();
        stub.codeSystem = "SNOMED-CT";
        stub.code = "38341003";
        Map<String, Object> attrs = firstAttributes(
                new ConditionsController(stub).listConditions("t1", "req-1", "corr-1", "patient-1"));
        assertEquals("38341003", attrs.get("code"));
        assertEquals("SNOMED-CT", attrs.get("codeSystem"));
        assertNull(attrs.get("icdCode"), "a SNOMED code must not be surfaced as an ICD code");
    }

    @Test
    void listRequiresAPatient() {
        ConditionsController controller = new ConditionsController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listConditions("t1", "req-1", "corr-1", "  ");
        assertEquals(400, response.getStatusCode().value());
    }

    // ── The write path ──────────────────────────────────────────────────────────

    @Test
    void createTranslatesTheRequestIntoPctsVocabulary() {
        StubPctClient stub = new StubPctClient();
        ConditionsController controller = new ConditionsController(stub);

        ResponseEntity<Map<String, Object>> response =
                controller.createCondition("t1", "pod-1", "req-2", "corr-2", null, request());

        assertEquals(201, response.getStatusCode().value());
        assertEquals("patient-1", stub.lastCreateBody.get("subject_cpid"));
        assertEquals("Hypertension", stub.lastCreateBody.get("display"));
        assertEquals("I10", stub.lastCreateBody.get("code"));
        assertEquals("ICD-10", stub.lastCreateBody.get("code_system"));
        assertEquals("MODERATE", stub.lastCreateBody.get("severity"));
    }

    /**
     * PCT stamps the author from the authenticated trust context. Forwarding a client-supplied
     * author would let the experience layer attribute a diagnosis to a clinician who never made it.
     */
    @Test
    void createDoesNotForwardAClientSuppliedAuthor() {
        StubPctClient stub = new StubPctClient();
        new ConditionsController(stub)
                .createCondition("t1", "pod-1", "req-2", "corr-2", null, request());
        assertFalse(stub.lastCreateBody.containsKey("recorded_by"),
                "the author must come from the trust context, never from the request body");
    }

    // ── Honesty when the record cannot be reached ───────────────────────────────

    @Test
    void listSaysUnavailableRatherThanReturningAnEmptyProblemList() {
        ResponseEntity<Map<String, Object>> response =
                new ConditionsController(new DownPctClient())
                        .listConditions("t1", "req-1", "corr-1", "patient-1");
        assertEquals(502, response.getStatusCode().value());
        assertEquals("condition_list_unavailable", response.getBody().get("error"));
        assertTrue(String.valueOf(response.getBody().get("message")).contains("absence"));
    }

    @Test
    void createSaysTheConditionWasNotRecorded() {
        ResponseEntity<Map<String, Object>> response =
                new ConditionsController(new DownPctClient())
                        .createCondition("t1", "pod-1", "req-2", "corr-2", null, request());
        assertEquals(502, response.getStatusCode().value());
        assertEquals("condition_not_recorded", response.getBody().get("error"));
    }

    @Test
    void resolveSaysTheConditionRemainsActive() {
        ResponseEntity<Map<String, Object>> response =
                new ConditionsController(new DownPctClient())
                        .resolveCondition(UUID.randomUUID(), "t1", "pod-1", "req-3", "corr-3", null);
        assertEquals(502, response.getStatusCode().value());
        assertEquals("condition_not_resolved", response.getBody().get("error"));
    }

    /**
     * A duplicate is a question, not an outage. Flattening PCT's 409 into the generic 502 would
     * tell the clinician their write broke and would lose the existing problem — which is the one
     * thing they need to see in order to answer.
     */
    @Test
    void createPassesADuplicateThroughAsAQuestionRatherThanAnOutage() {
        ResponseEntity<Map<String, Object>> response =
                new ConditionsController(new DuplicatePctClient())
                        .createCondition("t1", "pod-1", "req-2", "corr-2", null, request());

        assertEquals(409, response.getStatusCode().value());
        assertEquals("condition_already_on_list", response.getBody().get("error"));

        @SuppressWarnings("unchecked")
        Map<String, Object> existing = (Map<String, Object>) response.getBody().get("existing");
        assertNotNull(existing, "the clinician cannot answer without seeing what is already there");
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) existing.get("attributes");
        assertEquals("Hypertension", attrs.get("conditionName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resolutions =
                (List<Map<String, Object>>) response.getBody().get("resolutions");
        assertEquals(2, resolutions.size());
    }

    @Test
    void createForwardsTheCliniciansDuplicateAnswerToPct() {
        StubPctClient stub = new StubPctClient();
        new ConditionsController(stub).createCondition("t1", "pod-1", "req-2", "corr-2", null,
                new ConditionsController.CreateConditionRequest(
                        "patient-1", "enc-1", "jrn-1", "Hypertension", "I10",
                        "DIAGNOSIS", "ACTIVE", "MODERATE", null, "doc-1", "Notes",
                        "SAME_PROBLEM_RETURNING"));
        assertEquals("SAME_PROBLEM_RETURNING", stub.lastCreateBody.get("duplicate_resolution"));
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────

    private static ConditionsController.CreateConditionRequest request() {
        return new ConditionsController.CreateConditionRequest(
                "patient-1", "enc-1", "jrn-1", "Hypertension", "I10",
                "DIAGNOSIS", "ACTIVE", "MODERATE", null, "doc-1", "Notes", null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstResource(ResponseEntity<Map<String, Object>> response) {
        assertNotNull(response.getBody());
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertFalse(data.isEmpty(), "expected one condition");
        return data.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstAttributes(ResponseEntity<Map<String, Object>> response) {
        return (Map<String, Object>) firstResource(response).get("attributes");
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    /** Answers in the shape {@code ProblemController.toMap} actually emits. */
    private static class StubPctClient extends PctServiceClient {
        String code = "I10";
        String codeSystem = "ICD-10";
        Map<String, Object> lastCreateBody;

        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode listProblems(String subjectCpid) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(problem(subjectCpid));
            return arr;
        }

        @Override public JsonNode createProblem(Map<String, Object> body) {
            this.lastCreateBody = body;
            return problem(String.valueOf(body.get("subject_cpid")));
        }

        @Override public JsonNode resolveProblem(String problemId) {
            ObjectNode node = problem("patient-1");
            node.put("problem_id", problemId);
            node.put("clinical_status", "RESOLVED");
            return node;
        }

        ObjectNode problem(String subjectCpid) {
            ObjectNode node = mapper.createObjectNode();
            node.put("problem_id", "prob-1");
            node.put("subject_cpid", subjectCpid);
            node.put("journey_id", "jrn-1");
            node.put("encounter_id", "enc-1");
            node.put("code", code);
            node.put("code_system", codeSystem);
            node.put("display", "Hypertension");
            node.put("clinical_status", "ACTIVE");
            node.put("category", "DIAGNOSIS");
            node.put("severity", "MODERATE");
            node.put("onset_date", "2026-01-15");
            node.put("recorded_by", "clinician-9");
            node.put("notes", "Notes");
            node.put("created_at", "2026-01-15T09:00:00Z");
            return node;
        }
    }

    /** Answers a create with the 409 PCT raises when the problem is already on the list. */
    private static final class DuplicatePctClient extends StubPctClient {
        @Override public JsonNode createProblem(Map<String, Object> body) {
            ObjectNode upstream = mapper.createObjectNode();
            upstream.put("error", "problem_already_on_list");
            upstream.put("message", "A problem matching 'Hypertension' is already on this patient's list");
            upstream.set("existing", problem("patient-1"));
            throw HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.CONFLICT, "Conflict",
                    org.springframework.http.HttpHeaders.EMPTY,
                    upstream.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static final class DownPctClient extends StubPctClient {
        private static final RuntimeException DOWN = new IllegalStateException("pct unreachable");

        @Override public JsonNode listProblems(String subjectCpid) { throw DOWN; }
        @Override public JsonNode createProblem(Map<String, Object> body) { throw DOWN; }
        @Override public JsonNode resolveProblem(String problemId) { throw DOWN; }
    }
}
