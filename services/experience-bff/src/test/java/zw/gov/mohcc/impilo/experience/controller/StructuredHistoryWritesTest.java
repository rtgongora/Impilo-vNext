package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * history-writes (brief.md §6): the five POST routes existed on pct-service from the moment
 * {@code StructuredHistoryController} was written there; the BFF never called them, so the
 * Save/Add buttons on social-history, family-history and advance-directives had nowhere to send
 * a request. This proves each route now really forwards to pct rather than merely existing, and
 * that a failed write is reported as a failed write (never silently swallowed into a 200 that
 * would let the caller believe an entry was recorded when it was not).
 */
class StructuredHistoryWritesTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void recordSocialHistoryForwardsTheBodyAndReturnsWhatPctRecorded() {
        CapturingPctClient pct = new CapturingPctClient();
        StructuredHistoryController controller = controller(pct);

        var response = controller.recordSocialHistory("req-1", "corr-1",
                Map.of("subject_cpid", "CPID-1", "category", "TOBACCO", "status", "Never smoked",
                        "risk_level", "LOW"));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("CPID-1", pct.lastSocialHistoryBody.get("subject_cpid"));
        assertEquals("social-1", ((JsonNode) response.getBody().get("data")).get("id").asText());
    }

    @Test
    void recordFamilyMemberForwardsTheBody() {
        CapturingPctClient pct = new CapturingPctClient();
        StructuredHistoryController controller = controller(pct);

        var response = controller.recordFamilyMember("req-1", "corr-1",
                Map.of("subject_cpid", "CPID-1", "relationship", "MOTHER", "distinguisher", "J.M."));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("MOTHER", pct.lastFamilyMemberBody.get("relationship"));
    }

    @Test
    void aFamilyMemberValidationFailureIsReportedAsA400NotA502() {
        StructuredHistoryController controller = controller(new CapturingPctClient() {
            @Override
            public JsonNode recordFamilyMember(Map<String, Object> body) {
                throw new IllegalArgumentException(
                        "A cause or age of death was given for a relative not recorded as deceased.");
            }
        });

        var response = controller.recordFamilyMember("req-1", "corr-1",
                Map.of("subject_cpid", "CPID-1", "relationship", "MOTHER", "deceased_age", "70"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("family_member_invalid", response.getBody().get("error"));
    }

    @Test
    void recordFunctionalAssessmentForwardsTheBody() {
        CapturingPctClient pct = new CapturingPctClient();
        StructuredHistoryController controller = controller(pct);

        var response = controller.recordFunctionalAssessment("req-1", "corr-1",
                Map.of("subject_cpid", "CPID-1", "assessment_type", "ECOG", "total_score", 1, "max_score", 4));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("ECOG", pct.lastFunctionalBody.get("assessment_type"));
    }

    @Test
    void recordProcedureForwardsTheBody() {
        CapturingPctClient pct = new CapturingPctClient();
        StructuredHistoryController controller = controller(pct);

        var response = controller.recordProcedure("req-1", "corr-1",
                Map.of("subject_cpid", "CPID-1", "name", "Appendicectomy"));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("Appendicectomy", pct.lastProcedureBody.get("name"));
    }

    @Test
    void recordAdvanceDirectiveForwardsTheBody() {
        CapturingPctClient pct = new CapturingPctClient();
        StructuredHistoryController controller = controller(pct);

        var response = controller.recordAdvanceDirective("req-1", "corr-1",
                Map.of("subject_cpid", "CPID-1", "directive_type", "DNR", "summary", "Do not resuscitate"));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("DNR", pct.lastDirectiveBody.get("directive_type"));
    }

    @Test
    void aDownstreamFailureOnAnyWriteIsReportedNotSwallowed() {
        StructuredHistoryController controller = controller(new CapturingPctClient() {
            @Override
            public JsonNode recordAdvanceDirective(Map<String, Object> body) {
                throw new IllegalStateException("pct unreachable");
            }
        });

        var response = controller.recordAdvanceDirective("req-1", "corr-1",
                Map.of("subject_cpid", "CPID-1", "directive_type", "DNR"));

        assertEquals(502, response.getStatusCode().value());
        assertTrue(String.valueOf(response.getBody().get("message")).toLowerCase()
                .contains("nothing on the patient's record changed"));
    }

    private static StructuredHistoryController controller(PctServiceClient pct) {
        return new StructuredHistoryController(mapper, pct,
                new InpatientServiceClient(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), mapper));
    }

    private static class CapturingPctClient extends PctServiceClient {
        Map<String, Object> lastSocialHistoryBody;
        Map<String, Object> lastFamilyMemberBody;
        Map<String, Object> lastFunctionalBody;
        Map<String, Object> lastProcedureBody;
        Map<String, Object> lastDirectiveBody;

        CapturingPctClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), mapper);
        }

        @Override
        public JsonNode recordSocialHistory(Map<String, Object> body) {
            this.lastSocialHistoryBody = body;
            return mapper.createObjectNode().put("id", "social-1");
        }

        @Override
        public JsonNode recordFamilyMember(Map<String, Object> body) {
            this.lastFamilyMemberBody = body;
            return mapper.createObjectNode().put("id", "family-1");
        }

        @Override
        public JsonNode recordFunctionalAssessment(Map<String, Object> body) {
            this.lastFunctionalBody = body;
            return mapper.createObjectNode().put("id", "functional-1");
        }

        @Override
        public JsonNode recordProcedure(Map<String, Object> body) {
            this.lastProcedureBody = body;
            return mapper.createObjectNode().put("id", "procedure-1");
        }

        @Override
        public JsonNode recordAdvanceDirective(Map<String, Object> body) {
            this.lastDirectiveBody = body;
            return mapper.createObjectNode().put("id", "directive-1");
        }
    }
}
