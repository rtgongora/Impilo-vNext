package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A downstream that could not be reached must never be reported as a downstream that had nothing
 * to say. These endpoints used to answer a failed call with HTTP 200 and an empty payload, which
 * on a clinical surface is an affirmative finding — "no known allergies", "never immunised", "no
 * observations recorded" — asserted at the moment the system knew least.
 *
 * <p>Each case here drives the controller with a client that throws and asserts the response is a
 * 502 carrying an error code, a message that warns against reading the failure as an absence, and
 * the usual meta block. The complementary assertion matters just as much: the body must NOT carry
 * a {@code data} key, because a caller that reads {@code data} first would find an empty list and
 * be back where it started.</p>
 */
class DownstreamFailureHonestyTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final RuntimeException DOWN = new IllegalStateException("connection refused");

    // ── Shared assertions ───────────────────────────────────────────

    private static void assertHonestFailure(ResponseEntity<Map<String, Object>> response,
                                            String expectedErrorCode) {
        assertEquals(502, response.getStatusCode().value(),
                "a failed downstream call must not be reported as a successful empty read");
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(expectedErrorCode, body.get("error"));
        assertFalse(body.containsKey("data"),
                "the failure body must not carry a data key — an empty list there is exactly the "
                + "fabricated absence this contract exists to prevent");
        String message = String.valueOf(body.get("message"));
        assertTrue(message.toLowerCase().contains("do not treat this")
                        || message.toLowerCase().contains("not "),
                "the message must warn the caller against reading the failure as an absence, was: "
                + message);
    }

    private static void assertHasMeta(ResponseEntity<Map<String, Object>> response, String requestId) {
        Map<?, ?> meta = (Map<?, ?>) response.getBody().get("meta");
        assertNotNull(meta, "the existing meta block must survive the failure path");
        assertEquals(requestId, meta.get("request_id"));
    }

    // ── Clinical record lists ───────────────────────────────────────

    @Test
    @DisplayName("an unreachable PCT must not render as 'no known allergies'")
    void allergies() {
        ResponseEntity<Map<String, Object>> response =
                new AllergiesController(failingPct()).listAllergies("t", "req-1", "corr-1", "p-1");
        assertHonestFailure(response, "allergy_list_unavailable");
        assertHasMeta(response, "req-1");
    }

    @Test
    @DisplayName("an unreachable PCT must not render as 'no diagnosed conditions'")
    void conditions() {
        ResponseEntity<Map<String, Object>> response =
                new ConditionsController(failingPct())
                        .listConditions("t", "req-2", "corr-2", 0, 20, "p-1");
        assertHonestFailure(response, "condition_list_unavailable");
        assertHasMeta(response, "req-2");
    }

    @Test
    @DisplayName("an unreachable PCT must not render as 'unvaccinated'")
    void immunizations() {
        ResponseEntity<Map<String, Object>> response =
                new ImmunizationsController(failingPct())
                        .listImmunizations("t", "req-3", "corr-3", 0, 20, "p-1");
        assertHonestFailure(response, "immunization_history_unavailable");
        assertHasMeta(response, "req-3");
    }

    @Test
    @DisplayName("an unreachable PCT must not render as 'no observations recorded'")
    void vitals() {
        ResponseEntity<Map<String, Object>> response =
                new VitalsController(failingPct())
                        .listVitals("t", "req-4", "corr-4", 0, 20, "p-1", null);
        assertHonestFailure(response, "vitals_unavailable");
        assertHasMeta(response, "req-4");
    }

    @Test
    @DisplayName("an unreachable PCT must not render as 'no clinical history'")
    void timeline() {
        ResponseEntity<Map<String, Object>> response =
                new ClinicalTimelineController(failingPct())
                        .listTimeline("t", "req-5", "corr-5", 0, 20, "p-1", null);
        assertHonestFailure(response, "timeline_unavailable");
        assertHasMeta(response, "req-5");
    }

    @Test
    @DisplayName("a blank patient id is still a legitimate empty 200, not a failure")
    void blankPatientIdStillReturnsEmptyOk() {
        ResponseEntity<Map<String, Object>> response =
                new AllergiesController(failingPct()).listAllergies("t", "req-6", "corr-6", null);
        assertEquals(200, response.getStatusCode().value(),
                "the caller asked nothing of the downstream, so nothing failed");
        assertTrue(response.getBody().containsKey("data"));
    }

    // ── Decision support ────────────────────────────────────────────

    @Test
    @DisplayName("an unreachable rules engine must not clear a drug with zero alerts")
    void prescribingEvaluate() {
        ResponseEntity<Map<String, Object>> response =
                new ClinicalKnowledgeController(failingClinical())
                        .prescribing("t", null, null, Map.of());
        assertHonestFailure(response, "prescribing_check_unavailable");
    }

    @Test
    @DisplayName("an unreachable rules engine must not report an empty alert list")
    void rulesEvaluate() {
        ResponseEntity<Map<String, Object>> response =
                new ClinicalKnowledgeController(failingClinical()).rulesEvaluate("t", Map.of());
        assertHonestFailure(response, "rules_evaluation_unavailable");
    }

    // ── Orders ──────────────────────────────────────────────────────

    @Test
    @DisplayName("an unreachable OROS must not render as 'no orders or results'")
    void labOrdersList() {
        ResponseEntity<Map<String, Object>> response =
                new LabOrdersController(failingOros())
                        .listLabOrders("t", "req-7", "corr-7", 0, 20, "p-1", null, null);
        assertHonestFailure(response, "lab_orders_unavailable");
        assertHasMeta(response, "req-7");
    }

    @Test
    @DisplayName("a rejected acknowledgement must not be reported as REVIEWED")
    void labOrderAcknowledgeNotFabricated() {
        ResponseEntity<Map<String, Object>> response =
                new LabOrdersController(failingOros())
                        .acknowledgeLabOrder("order-1", "t", "pod", "req-8", "corr-8", null, null);
        assertHonestFailure(response, "lab_acknowledgement_not_recorded");
        assertHasMeta(response, "req-8");
    }

    @Test
    @DisplayName("a rejected cancellation must not be reported as CANCELLED")
    void labOrderCancelNotFabricated() {
        ResponseEntity<Map<String, Object>> response =
                new LabOrdersController(failingOros())
                        .cancelLabOrder("order-1", "t", "pod", "req-9", "corr-9", null, null);
        assertHonestFailure(response, "lab_order_not_cancelled");
        assertHasMeta(response, "req-9");
    }

    // ── Audit ───────────────────────────────────────────────────────

    @Test
    @DisplayName("an unreachable audit store must not render as 'nobody accessed this record'")
    void auditList() {
        ResponseEntity<Map<String, Object>> response =
                new AuditController(failingAudit())
                        .listAuditEntries("t", "req-10", "corr-10", 0, 20, null, null);
        assertHonestFailure(response, "audit_trail_unavailable");
        assertHasMeta(response, "req-10");
    }

    // ── Failing stubs ───────────────────────────────────────────────

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static PctServiceClient failingPct() {
        return new PctServiceClient(new RestTemplate(), endpoints(), mapper) {
            @Override public JsonNode listAllergies(String cpid) { throw DOWN; }
            @Override public JsonNode listConditions(String cpid, int page, int size) { throw DOWN; }
            @Override public JsonNode listImmunizations(String cpid, int page, int size) { throw DOWN; }
            @Override public JsonNode listVitals(String cpid, int page, int size) { throw DOWN; }
            @Override public JsonNode getPatientTimeline(String cpid) { throw DOWN; }
        };
    }

    private static ClinicalKnowledgePlatformClient failingClinical() {
        return new ClinicalKnowledgePlatformClient(
                new RestTemplate(), new ClinicalPlatformProperties("http://clinical")) {
            @Override public JsonNode prescribingEvaluate(Map<String, Object> body) { throw DOWN; }
            @Override public JsonNode rulesEvaluate(Map<String, Object> body) { throw DOWN; }
        };
    }

    private static OrosServiceClient failingOros() {
        return new OrosServiceClient(new RestTemplate(), endpoints()) {
            @Override public JsonNode getPatientOrders(String patientId) { throw DOWN; }
            @Override public JsonNode listOrdersByEncounter(String encounterId) { throw DOWN; }
            @Override public JsonNode acknowledgeOrder(String id, String by, String notes) { throw DOWN; }
            @Override public JsonNode cancelOrder(String id, String reason) { throw DOWN; }
        };
    }

    private static TshepoAuditServiceClient failingAudit() {
        return new TshepoAuditServiceClient(new RestTemplate(), endpoints()) {
            @Override public JsonNode queryEvents(String actorId, String subjectRef, String eventType,
                                                  String fromTime, String toTime, int page, int size) {
                throw DOWN;
            }
            @Override public JsonNode getEvent(UUID id) { throw DOWN; }
        };
    }
}
