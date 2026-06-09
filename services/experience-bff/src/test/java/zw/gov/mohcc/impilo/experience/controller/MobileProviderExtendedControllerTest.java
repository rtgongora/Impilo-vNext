package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PacsServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MobileProviderExtendedControllerTest {

    @Test
    void billingChargesProxiesCostaBillForEncounter() {
        MobileProviderExtendedController controller = newController(
                new StubPctClient(), new StubVitoClient(), new StubPharmacyClient(), new BillingCostaClient(), new StubOrosClient());

        ResponseEntity<Map<String, Object>> response = controller.getCharges("tenant-1", "enc-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("bill-1", ((com.fasterxml.jackson.databind.JsonNode) response.getBody().get("data")).get("id").asText());
    }

    @Test
    void billingChargeCaptureProxiesCostaEstimate() {
        MobileProviderExtendedController controller = newController(
                new StubPctClient(), new StubVitoClient(), new StubPharmacyClient(), new BillingCostaClient(), new StubOrosClient());

        ResponseEntity<Map<String, Object>> response = controller.captureCharge("tenant-1", Map.of("encounterId", "enc-1"));
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("estimate-1", ((com.fasterxml.jackson.databind.JsonNode) response.getBody().get("data")).get("id").asText());
    }

    @Test
    void pharmacyPendingReturnsWorklistFromUpstream() {
        MobileProviderExtendedController controller = newController(
                new StubPctClient(), new StubVitoClient(), new WorklistPharmacyClient(), new StubCostaClient(), new StubOrosClient());

        ResponseEntity<Map<String, Object>> response = controller.getPendingDispensing("tenant-1", "facility-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, ((java.util.List<?>) response.getBody().get("data")).size());
    }

    @Test
    void pharmacyVerifyFiveRightsUsesPrescriptionPayload() {
        MobileProviderExtendedController controller = newController(
                new StubPctClient(), new StubVitoClient(), new PrescriptionPharmacyClient(), new StubCostaClient(), new StubOrosClient());

        ResponseEntity<Map<String, Object>> response = controller.verifyFiveRights(
                Map.of("prescriptionId", "550e8400-e29b-41d4-a716-446655440000", "patient_id", "cpid-1"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("verified"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static MobileProviderExtendedController newController(
            PctServiceClient pctClient,
            VitoServiceClient vitoClient,
            PharmacyServiceClient pharmacyClient,
            CostaServiceClient costaClient,
            OrosServiceClient orosClient) {
        return new MobileProviderExtendedController(
                pctClient, vitoClient, pharmacyClient, costaClient, orosClient,
                new StubPacsClient(), new StubInpatientClient());
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), new ObjectMapper()); }
    }

    private static final class StubVitoClient extends VitoServiceClient {
        StubVitoClient() { super(new RestTemplate(), endpoints()); }
    }

    private static final class StubPharmacyClient extends PharmacyServiceClient {
        StubPharmacyClient() { super(new RestTemplate(), endpoints()); }
    }

    private static final class WorklistPharmacyClient extends PharmacyServiceClient {
        WorklistPharmacyClient() { super(new RestTemplate(), endpoints()); }

        @Override
        public com.fasterxml.jackson.databind.JsonNode getWorklist(String facilityId, String status) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ArrayNode rows = mapper.createArrayNode();
            rows.addObject()
                    .put("id", "rx-1")
                    .put("medication_name", "Amoxicillin")
                    .put("patient_id", "cpid-1");
            return rows;
        }
    }

    private static final class PrescriptionPharmacyClient extends PharmacyServiceClient {
        PrescriptionPharmacyClient() { super(new RestTemplate(), endpoints()); }

        @Override
        public com.fasterxml.jackson.databind.JsonNode getPrescription(String prescriptionId) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode attrs = mapper.createObjectNode();
            attrs.put("patient_id", "cpid-1");
            attrs.put("medication_name", "Amoxicillin");
            attrs.put("dosage", "500mg");
            attrs.put("route", "PO");
            attrs.put("frequency", "TDS");
            attrs.put("status", "ACTIVE");
            return mapper.createObjectNode()
                    .put("id", prescriptionId)
                    .put("type", "prescription")
                    .set("attributes", attrs);
        }
    }

    private static final class StubCostaClient extends CostaServiceClient {
        StubCostaClient() { super(new RestTemplate(), endpoints()); }
    }

    private static final class BillingCostaClient extends CostaServiceClient {
        BillingCostaClient() { super(new RestTemplate(), endpoints()); }

        @Override
        public com.fasterxml.jackson.databind.JsonNode createBillDraft(String encounterId, String billType) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.createObjectNode().put("id", "bill-1").put("encounterId", encounterId);
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode getBill(String billId) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.createObjectNode().put("id", billId).put("status", "DRAFT");
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode createEstimate(java.util.Map<String, Object> body) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.createObjectNode().put("id", "estimate-1");
        }
    }

    private static final class StubOrosClient extends OrosServiceClient {
        StubOrosClient() { super(new RestTemplate(), endpoints()); }
    }

    private static final class StubPacsClient extends PacsServiceClient {
        StubPacsClient() { super(new RestTemplate(), endpoints(), new ObjectMapper()); }
    }

    private static final class StubInpatientClient extends InpatientServiceClient {
        StubInpatientClient() { super(new RestTemplate(), endpoints(), new ObjectMapper()); }
    }
}
