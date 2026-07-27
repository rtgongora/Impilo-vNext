package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HIV/TB programme surface, as the experience layer serves it.
 *
 * <p>The assertions that earn their keep are the negative ones: a failed read must never become an
 * empty success, because "not in any programme" and "on no treatment" are affirmative clinical
 * claims a broken call must not be able to make. This is the same defect class the problem-list
 * vertical was built to close, applied to programmes.</p>
 */
class ProgrammesControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listForwardsEnrolmentsIncludingTheConfidentialFlag() {
        ProgrammesController controller = new ProgrammesController(new StubPct(), new StubCkp());
        ResponseEntity<Map<String, Object>> response =
                controller.list("req-1", "corr-1", "patient-1", false);

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("HIV_CARE", data.get(0).get("programme").asText());
        // HIV is confidential; the surface needs the flag to present it on the confidential lane.
        assertTrue(data.get(0).get("confidential").asBoolean());
    }

    @Test
    void aFailedListIsAGatewayErrorNotAnEmptyProgrammeList() {
        StubPct pct = new StubPct();
        pct.failReads = true;
        ProgrammesController controller = new ProgrammesController(pct, new StubCkp());

        ResponseEntity<Map<String, Object>> response =
                controller.list("req-1", "corr-1", "patient-1", false);

        // The core property: a broken read must not read as "this patient is in no programme".
        assertEquals(HttpStatus.BAD_GATEWAY.value(), response.getStatusCode().value());
        assertEquals("programme_list_unavailable", response.getBody().get("error"));
    }

    @Test
    void missingPatientIsRejectedNotProxied() {
        ProgrammesController controller = new ProgrammesController(new StubPct(), new StubCkp());
        ResponseEntity<Map<String, Object>> response = controller.list("req-1", "corr-1", "  ", false);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("patient_id_required", response.getBody().get("error"));
    }

    @Test
    void enrolTranslatesTheUiPatientIdOntoSubjectCpid() {
        StubPct pct = new StubPct();
        ProgrammesController controller = new ProgrammesController(pct, new StubCkp());

        controller.enrol("req-1", "corr-1", Map.of(
                "patientId", "patient-1",
                "programme", "HIV_CARE",
                "anchorProblemId", "11111111-1111-1111-1111-111111111111"));

        // The UI says patientId; pct's programme record keys on subject_cpid. The translation must
        // happen, or pct binds a null subject and the enrolment is rejected.
        assertEquals("patient-1", pct.lastEnrolBody.get("subject_cpid"));
        // Everything else is forwarded untouched so a field pct reads in either spelling still lands.
        assertEquals("HIV_CARE", pct.lastEnrolBody.get("programme"));
    }

    @Test
    void aDuplicateEnrolmentIsPassedThroughAs409() {
        StubPct pct = new StubPct();
        pct.conflictOnEnrol = true;
        ProgrammesController controller = new ProgrammesController(pct, new StubCkp());

        ResponseEntity<Map<String, Object>> response = controller.enrol("req-1", "corr-1", Map.of(
                "patientId", "patient-1", "programme", "HIV_CARE",
                "anchorProblemId", "11111111-1111-1111-1111-111111111111"));

        assertEquals(HttpStatus.CONFLICT.value(), response.getStatusCode().value());
        assertEquals("programme_already_enrolled", response.getBody().get("error"));
    }

    @Test
    void aFailedRegimenReadIsAGatewayErrorNotOnNoTreatment() {
        StubPct pct = new StubPct();
        pct.failReads = true;
        ProgrammesController controller = new ProgrammesController(pct, new StubCkp());

        ResponseEntity<Map<String, Object>> response =
                controller.regimens("enrol-1", "req-1", "corr-1");

        assertEquals(HttpStatus.BAD_GATEWAY.value(), response.getStatusCode().value());
        assertEquals("regimens_unavailable", response.getBody().get("error"));
    }

    @Test
    void failedGuidanceDoesNotReadAsAnAllClear() {
        StubCkp ckp = new StubCkp();
        ckp.fail = true;
        ProgrammesController controller = new ProgrammesController(new StubPct(), ckp);

        ResponseEntity<Map<String, Object>> response =
                controller.guidance("HIV_CARE", "req-1", "corr-1", Map.of("viralLoadCopies", 40));

        assertEquals(HttpStatus.BAD_GATEWAY.value(), response.getStatusCode().value());
        assertEquals("guidance_unavailable", response.getBody().get("error"));
    }

    @Test
    void guidanceForwardsTheCkpResult() {
        ProgrammesController controller = new ProgrammesController(new StubPct(), new StubCkp());
        ResponseEntity<Map<String, Object>> response =
                controller.guidance("HIV_CARE", "req-1", "corr-1", Map.of("viralLoadCopies", 1200));
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    // ── stubs ───────────────────────────────────────────────────────────────────

    private static class StubPct extends PctServiceClient {
        boolean failReads = false;
        boolean conflictOnEnrol = false;
        Map<String, Object> lastEnrolBody;

        StubPct() { super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), mapper); }

        @Override public JsonNode listProgrammeEnrolments(String subjectCpid, boolean openOnly) {
            if (failReads) throw new RuntimeException("pct down");
            ArrayNode arr = mapper.createArrayNode();
            ObjectNode e = mapper.createObjectNode();
            e.put("enrolment_id", "enrol-1");
            e.put("subject_cpid", subjectCpid);
            e.put("programme", "HIV_CARE");
            e.put("status", "ON_TREATMENT");
            e.put("confidential", true);
            arr.add(e);
            return arr;
        }

        @Override public JsonNode enrolProgramme(Map<String, Object> body) {
            this.lastEnrolBody = body;
            if (conflictOnEnrol) {
                throw HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict",
                        org.springframework.http.HttpHeaders.EMPTY,
                        ("{\"error\":\"programme_already_enrolled\",\"message\":\"already enrolled\","
                         + "\"active_enrolment_id\":\"enrol-1\",\"programme\":\"HIV_CARE\"}")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
            ObjectNode e = mapper.createObjectNode();
            e.put("enrolment_id", "enrol-1");
            e.put("programme", String.valueOf(body.get("programme")));
            return e;
        }

        @Override public JsonNode listProgrammeRegimens(String enrolmentId) {
            if (failReads) throw new RuntimeException("pct down");
            return mapper.createArrayNode();
        }
    }

    private static class StubCkp extends ClinicalKnowledgePlatformClient {
        boolean fail = false;

        StubCkp() { super(new RestTemplate(), new ClinicalPlatformProperties("http://ckp.test")); }

        @Override public JsonNode programmeGuidance(String programme, Map<String, Object> body) {
            if (fail) throw new RuntimeException("ckp down");
            ObjectNode out = mapper.createObjectNode();
            out.put("assessed", true);
            out.putArray("alerts");
            return out;
        }
    }
}
