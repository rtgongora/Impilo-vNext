package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MobilePrescriptionControllerTest {

    @Test
    void createPrescriptionReturnsCreatedWhenBackendWriteIsWired() {
        MobilePrescriptionController controller = new MobilePrescriptionController(new StubPharmacyClient());
        MobilePrescriptionController.CreatePrescriptionRequest request =
                new MobilePrescriptionController.CreatePrescriptionRequest(
                        "cpid-1",
                        "enc-1",
                        "fac-1",
                        "Amoxicillin",
                        "Amoxicillin",
                        "500mg",
                        "PO",
                        "TID",
                        "7 days",
                        21,
                        "Take with food",
                        "Infection",
                        "clinician-1");

        var response = controller.createPrescription("tenant-1", "pod-1", "req-1", "corr-1", null, request);

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("rx-1", ((JsonNode) response.getBody().get("data")).get("id").asText());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static final class StubPharmacyClient extends PharmacyServiceClient {
        private StubPharmacyClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode createPrescription(Map<String, Object> body) {
            com.fasterxml.jackson.databind.node.ObjectNode node = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            node.put("id", "rx-1");
            node.put("type", "prescription");
            return node;
        }
    }
}
