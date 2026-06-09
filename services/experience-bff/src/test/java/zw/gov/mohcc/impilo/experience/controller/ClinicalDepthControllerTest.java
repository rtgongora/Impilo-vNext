package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClinicalDepthControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void addGoal_returnsCreatedWhenPctSucceeds() {
        ClinicalDepthController controller = new ClinicalDepthController(new CapturingPctClient(), new StubInpatientClient());
        UUID planId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        ResponseEntity<Map<String, Object>> response = controller.addGoal(
                planId,
                Map.of("description", "Lower HbA1c", "category", "Lab"));

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
        assertEquals(planId.toString(), CapturingPctClient.lastPlanId);
        assertEquals("Lower HbA1c", CapturingPctClient.lastGoalBody.get("description"));
    }

    @Test
    void initClearances_usesInpatientSoR() {
        ClinicalDepthController controller = new ClinicalDepthController(new CapturingPctClient(), new StubInpatientClient());
        ResponseEntity<Map<String, Object>> response = controller.initClearances(
                "tenant-1",
                Map.of("encounterId", "b1000000-0000-0000-0000-000000000001", "patientId", "CPID-ZW-00001"));
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void startPhase_usesInpatientSoR() {
        ClinicalDepthController controller = new ClinicalDepthController(new CapturingPctClient(), new StubInpatientClient());
        UUID activationId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        ResponseEntity<Map<String, Object>> response = controller.startPhase(
                activationId, Map.of("phase", "RESUSCITATION", "rhythm", "Asystole"));
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void performIntervention_returnsOkWhenPctSucceeds() {
        ClinicalDepthController controller = new ClinicalDepthController(new CapturingPctClient(), new StubInpatientClient());
        UUID planId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID intId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        ResponseEntity<Map<String, Object>> response = controller.performIntervention(planId, intId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("performed"));
        assertEquals(intId.toString(), CapturingPctClient.lastInterventionId);
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubInpatientClient extends InpatientServiceClient {
        StubInpatientClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public JsonNode logEmergencyAction(String activationId, Map<String, Object> body) {
            return MAPPER.createObjectNode().put("id", "action-1");
        }

        @Override
        public JsonNode listEmergencyActions(String activationId, String actionType) {
            return MAPPER.createArrayNode();
        }

        @Override
        public JsonNode initDischargeClearances(Map<String, Object> body) {
            return MAPPER.createArrayNode().add(MAPPER.createObjectNode()
                    .put("id", "clearance-1")
                    .put("clearance_type", "CLINICAL")
                    .put("status", "PENDING"));
        }

        @Override
        public JsonNode getDischargeClearances(String encounterId) {
            ObjectNode payload = MAPPER.createObjectNode();
            payload.set("data", MAPPER.createArrayNode());
            payload.set("progress", MAPPER.createObjectNode().put("total", 0).put("cleared", 0).put("percent", 0));
            return payload;
        }

        @Override
        public JsonNode startResuscitationPhase(String activationId, Map<String, Object> body) {
            return MAPPER.createObjectNode().put("id", "phase-1").put("phase_type", "RESUSCITATION");
        }
    }

    private static final class CapturingPctClient extends PctServiceClient {
        static String lastPlanId;
        static Map<String, Object> lastGoalBody;
        static String lastInterventionId;

        CapturingPctClient() {
            super(new RestTemplate(), endpoints(), MAPPER);
        }

        @Override
        public ObjectNode addCarePlanGoal(String planId, Map<String, Object> body) {
            lastPlanId = planId;
            lastGoalBody = body;
            return MAPPER.createObjectNode().put("id", "goal-1");
        }

        @Override
        public ObjectNode performCarePlanIntervention(String planId, String interventionId) {
            lastPlanId = planId;
            lastInterventionId = interventionId;
            return MAPPER.createObjectNode().put("performed", true);
        }
    }
}
