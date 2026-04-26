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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImmunizationsControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listImmunizations_returnsDataAndMeta() {
        ImmunizationsController controller = new ImmunizationsController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listImmunizations("t1", "req-1", "corr-1", 0, 20, "patient-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listImmunizations_emptyPatientId_returnsEmptyData() {
        ImmunizationsController controller = new ImmunizationsController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listImmunizations("t1", "req-2", "corr-2", 0, 20, null);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void createImmunization_returns201() {
        ImmunizationsController controller = new ImmunizationsController(new StubPctClient());
        ImmunizationsController.CreateImmunizationRequest request =
                new ImmunizationsController.CreateImmunizationRequest(
                        "patient-1", "enc-1", "BCG", "BCG-001",
                        1, "PRIMARY", "LOT-123", "LEFT_ARM", "IM",
                        "nurse-1", null, "First dose");
        ResponseEntity<Map<String, Object>> response =
                controller.createImmunization("t1", "pod-1", "req-3", "corr-3", null, request);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-3", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null
        );
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode listImmunizations(String patientCpid, int page, int size) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "imm-1").put("vaccine_name", "BCG"));
            return arr;
        }

        @Override public JsonNode createImmunization(Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", "imm-new");
            node.put("vaccine_name", body.get("vaccine_name").toString());
            return node;
        }
    }
}
