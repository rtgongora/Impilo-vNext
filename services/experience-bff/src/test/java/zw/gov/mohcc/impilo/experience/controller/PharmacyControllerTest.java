package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PharmacyControllerTest {

    @Test
    void createPrescriptionReturnsCreatedWhenBackendWriteIsWired() {
        PharmacyController controller = new PharmacyController(new StubPharmacyClient());
        PharmacyController.CreatePrescriptionRequest request = new PharmacyController.CreatePrescriptionRequest(
                "cpid-1",
                "facility-1",
                "enc-1",
                "Amoxicillin",
                "Amoxicillin",
                "500mg",
                "PO",
                "TID",
                "7 days",
                21,
                "Take with food",
                "URI",
                "clinician-1");

        var response = controller.createPrescription("tenant-1", "pod-1", "req-1", "corr-1", null, request);

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("rx-1", ((JsonNode) response.getBody().get("data")).get("id").asText());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listPrescriptionsReturnsBadGatewayWhenPharmacyUnavailable() {
        PharmacyController controller = new PharmacyController(new FailingPharmacyClient());

        var response = controller.listPrescriptions("tenant-1", "req-2", "corr-2", 0, 20, null, "cpid-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PHARMACY_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void dispenseReturnsBadRequestWhenPrescriptionIdIsInvalid() {
        PharmacyController controller = new PharmacyController(new StubPharmacyClient());
        PharmacyController.DispenseRequest request = new PharmacyController.DispenseRequest("not-a-uuid", "pharm-1");

        var response = controller.dispense("tenant-1", "pod-1", "req-3", "corr-3", null, request);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INVALID_PRESCRIPTION_ID", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class StubPharmacyClient extends PharmacyServiceClient {
        private StubPharmacyClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode createPrescription(Map<String, Object> body) {
            com.fasterxml.jackson.databind.node.ObjectNode node = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            node.put("id", "rx-1");
            node.put("type", "prescription");
            return node;
        }

        @Override
        public JsonNode dispensePrescription(java.util.UUID prescriptionId, Map<String, Object> body) {
            com.fasterxml.jackson.databind.node.ObjectNode node = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            node.put("id", prescriptionId.toString());
            node.put("status", "DISPENSED");
            return node;
        }

        @Override
        public JsonNode cancelPrescription(java.util.UUID prescriptionId, Map<String, Object> body) {
            com.fasterxml.jackson.databind.node.ObjectNode node = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            node.put("id", prescriptionId.toString());
            node.put("status", "CANCELLED");
            return node;
        }
    }

    private static final class FailingPharmacyClient extends PharmacyServiceClient {
        private FailingPharmacyClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode getPatientPrescriptions(String cpid, String status, int page, int size) {
            throw new RuntimeException("pharmacy unavailable");
        }
    }
}
