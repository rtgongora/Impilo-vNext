package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CareEmergencyInpatientControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listCarePlans_returnsDataFromPct() {
        CareEmergencyInpatientController controller =
                new CareEmergencyInpatientController(new StubInpatientClient(), new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listCarePlans("t1", "patient-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void listActivations_returnsEmergencyData() {
        CareEmergencyInpatientController controller =
                new CareEmergencyInpatientController(new StubInpatientClient(), new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listActivations("t1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void createAdmission_returns201() {
        CareEmergencyInpatientController controller =
                new CareEmergencyInpatientController(new StubInpatientClient(), new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.createAdmission("t1", Map.of("patientId", "p1", "wardId", "w1"));
        assertEquals(201, response.getStatusCode().value());
        Object data = response.getBody().get("data");
        assertNotNull(data);
        JsonNode dataNode = data instanceof JsonNode j ? j : mapper.valueToTree(data);
        assertNotNull(dataNode.get("id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode listCarePlans(String patientId) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "cp-1").put("status", "ACTIVE"));
            return arr;
        }

        // No listEmergencyActivations override: emergency activations were never served by PCT.
        // The stub used to return an empty array here while the inpatient stub below returned the
        // real fixture, which is what a vestigial defensive stub looks like.

        @Override public JsonNode createCarePlan(Map<String, Object> body) {
            return mapper.createObjectNode().put("id", "cp-new");
        }
    }

    private static final class StubInpatientClient extends InpatientServiceClient {
        StubInpatientClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode listCarePlans(String patientId) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "cp-1").put("status", "ACTIVE"));
            return arr;
        }

        @Override public JsonNode listEmergencyActivations() {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode()
                    .put("id", "ea-1")
                    .put("protocol_type", "CODE_BLUE")
                    .put("status", "ACTIVE"));
            return arr;
        }

        @Override public JsonNode createAdmission(Map<String, Object> request) {
            return mapper.createObjectNode().put("id", "adm-1");
        }

        @Override public JsonNode listAdmissions(String patientCpid) {
            return mapper.createArrayNode();
        }
    }
}
