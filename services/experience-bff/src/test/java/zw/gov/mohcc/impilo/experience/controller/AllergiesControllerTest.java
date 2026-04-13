package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AllergiesControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listAllergies_returnsDataAndMeta() {
        AllergiesController controller = new AllergiesController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listAllergies("t1", "req-1", "corr-1", "patient-123");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listAllergies_emptyPatientId_returnsEmptyData() {
        AllergiesController controller = new AllergiesController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listAllergies("t1", "req-2", "corr-2", null);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void createAllergy_returns201WithMeta() {
        AllergiesController controller = new AllergiesController(new StubPctClient());
        AllergiesController.CreateAllergyRequest request =
                new AllergiesController.CreateAllergyRequest(
                        "patient-1", "Peanuts", "FOOD", "Hives", "SEVERE", null, "doc-1");
        ResponseEntity<Map<String, Object>> response =
                controller.createAllergy("t1", "pod-1", "req-3", "corr-3", null, request);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-3", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                "http://pct", "http://oros", "http://pharmacy", "http://butano",
                "http://msika", "http://msika-flow", "http://mushex", "http://vito",
                "http://tuso", "http://varapi", "http://documents", "http://costa",
                "http://coverage", "http://surveillance", "http://campaigns",
                "http://indawo", "http://governance", "http://landela",
                "http://notifications"
        );
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode listAllergies(String patientCpid) {
            ObjectNode node = mapper.createObjectNode();
            node.putArray("items");
            return node;
        }

        @Override public JsonNode createAllergy(Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", UUID.randomUUID().toString());
            node.put("allergen", body.get("allergen").toString());
            return node;
        }

        @Override public JsonNode deactivateAllergy(String allergyId) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", allergyId);
            node.put("status", "INACTIVE");
            return node;
        }
    }
}
