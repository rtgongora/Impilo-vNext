package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobilePrescriptionControllerTest {

    @Test
    void createPrescriptionReturnsCreatedWhenBackendWriteIsWired() {
        MobilePrescriptionController controller = new MobilePrescriptionController(new StubPharmacyClient());
        MobilePrescriptionController.CreatePrescriptionRequest request =
                new MobilePrescriptionController.CreatePrescriptionRequest(
                        "cpid-1",
                        "enc-1",
                        "fac-1",
                        null,
                        "Amoxicillin",
                        "Amoxicillin",
                        "500mg",
                        "PO",
                        "TID",
                        "7 days",
                        21,
                        "Take with food",
                        "Infection",
                        null);

        var response = controller.createPrescription(
                "tenant-1", "pod-1", "req-1", "corr-1", null, "clinician-1", request);

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("rx-1", ((JsonNode) response.getBody().get("data")).get("id").asText());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listPrescriptionsFiltersByEncounterWhenRequested() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode rows = mapper.createArrayNode();
        rows.add(mapper.readTree("""
                {"id":"rx-1","type":"prescription","attributes":{"encounter_id":"enc-1","patient_id":"cpid-1","medication_name":"A"}}
                """));
        rows.add(mapper.readTree("""
                {"id":"rx-2","type":"prescription","attributes":{"encounter_id":"enc-2","patient_id":"cpid-1","medication_name":"B"}}
                """));

        MobilePrescriptionController controller = new MobilePrescriptionController(new StubPharmacyClient(rows));
        var response = controller.listPrescriptions(
                "tenant-1", "req-1", "corr-1", "enc-1", "cpid-1", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertTrue(data.isArray());
        assertEquals(1, data.size());
        assertEquals("rx-1", data.get(0).get("id").asText());
    }

    private static final class StubPharmacyClient extends PharmacyServiceClient {
        private final JsonNode listData;

        private StubPharmacyClient() {
            this(null);
        }

        private StubPharmacyClient(JsonNode listData) {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
            this.listData = listData;
        }

        @Override
        public JsonNode createPrescription(Map<String, Object> body) {
            com.fasterxml.jackson.databind.node.ObjectNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            node.put("id", "rx-1");
            node.put("type", "prescription");
            return node;
        }

        @Override
        public JsonNode getPatientPrescriptions(String cpid, String status, int page, int size) {
            return listData;
        }
    }
}
