package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeleconsultControllerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void createSessionProxiesToPctReferralDraft() {
        TeleconsultController controller = controller(new StubPctClient(), new StubDocumentClient());
        ResponseEntity<Map<String, Object>> response = controller.createSession(
                "req-1", "corr-1", "provider-1", Map.of("patientId", "CPID-1", "encounterId", "11"));
        assertEquals(201, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("ref-1", data.get("id").asText());
    }

    @Test
    void recordConsentReturnsMvumoReferenceAndUpdatesPct() {
        TeleconsultController controller = controller(new StubPctClient(), new StubDocumentClient());
        ResponseEntity<Map<String, Object>> response = controller.recordConsent(
                "ref-1", "req-2", "corr-2", Map.of("type", "DIGITAL", "patientId", "CPID-1"));
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = MAPPER.valueToTree(response.getBody().get("data"));
        assertEquals("mvumo-1", data.get("consentToken").asText());
    }

    @Test
    void updateReferralRejectsInvalidAttachmentReference() {
        TeleconsultController controller = controller(new StubPctClient(), new StubDocumentClient());
        ResponseEntity<Map<String, Object>> response = controller.updateReferral(
                "ref-1", "req-3", "corr-3",
                Map.of("attachments", java.util.List.of("not-a-uuid"), "routingType", "PRACTITIONER", "routingTarget", "PROV-1"));
        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("INVALID_ATTACHMENT_REFERENCE", error.get("code"));
    }

    @Test
    void updateReferralRejectsUnsupportedRoutingType() {
        TeleconsultController controller = controller(new StubPctClient(), new StubDocumentClient());
        ResponseEntity<Map<String, Object>> response = controller.updateReferral(
                "ref-1", "req-4", "corr-4",
                Map.of("attachments", java.util.List.of(UUID.randomUUID().toString()), "routingType", "ON_CALL", "routingTarget", "TEAM-A"));
        assertEquals(501, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("ROUTING_TYPE_UNAVAILABLE", error.get("code"));
    }

    @Test
    void submitBlocksWhenStoredAttachmentMissingInDocumentService() {
        TeleconsultController controller = controller(new StubPctClientWithStoredMissingAttachment(), new StubDocumentClientMissing());
        ResponseEntity<Map<String, Object>> response = controller.submitReferral("ref-1", "req-5", "corr-5");
        assertEquals(400, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("INVALID_ATTACHMENT_REFERENCE", error.get("code"));
    }

    private TeleconsultController controller(PctServiceClient pct, DocumentServiceClient docs) {
        return new TeleconsultController(
                pct,
                new StubMvumoClient(),
                docs,
                new StubVarapiClient(),
                new StubTusoClient(),
                MAPPER);
    }

    private static class StubPctClient extends PctServiceClient {
        StubPctClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), MAPPER);
        }

        @Override
        public JsonNode createReferral(Map<String, Object> request) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", "ref-1");
            node.put("patientCpid", request.get("patient_id").toString());
            return node;
        }

        @Override
        public JsonNode getReferral(String referralId) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", referralId);
            node.put("patientCpid", "CPID-1");
            return node;
        }

        @Override
        public JsonNode updateReferralConsent(String referralId, Map<String, Object> request) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", referralId);
            node.put("consentStatus", String.valueOf(request.get("consent_status")));
            return node;
        }

        @Override
        public JsonNode updateReferralStage(String referralId, Map<String, Object> request) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", referralId);
            node.put("routingTarget", "{\"type\":\"PRACTITIONER\",\"target_ref\":\"PROV-1\"}");
            node.put("attachmentDocumentIds", "[\"11111111-1111-1111-1111-111111111111\"]");
            return node;
        }

        @Override
        public JsonNode submitReferral(String referralId) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", referralId);
            node.put("status", "SUBMITTED");
            return node;
        }
    }

    private static final class StubPctClientWithStoredMissingAttachment extends StubPctClient {
        @Override
        public JsonNode getReferral(String referralId) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", referralId);
            node.put("routingTarget", "{\"type\":\"PRACTITIONER\",\"target_ref\":\"PROV-1\"}");
            node.put("attachmentDocumentIds", "[\"11111111-1111-1111-1111-111111111111\"]");
            return node;
        }
    }

    private static final class StubMvumoClient extends MvumoServiceClient {
        StubMvumoClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode createConsentRequest(Map<String, Object> request) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", "mvumo-1");
            node.put("tshepoConsentId", "tshepo-1");
            return node;
        }
    }

    private static class StubDocumentClient extends DocumentServiceClient {
        StubDocumentClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode getObjectMetadata(UUID objectId) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("objectId", objectId.toString());
            return node;
        }
    }

    private static final class StubDocumentClientMissing extends StubDocumentClient {
        @Override
        public JsonNode getObjectMetadata(UUID objectId) {
            throw HttpClientErrorException.create(org.springframework.http.HttpStatus.NOT_FOUND,
                    "not found", org.springframework.http.HttpHeaders.EMPTY, new byte[0], java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static final class StubVarapiClient extends VarapiServiceClient {
        StubVarapiClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode getProvider(String providerId) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("providerPublicId", providerId);
            return node;
        }
    }

    private static final class StubTusoClient extends TusoServiceClient {
        StubTusoClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode getWorkspace(UUID workspaceId) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", workspaceId.toString());
            return node;
        }

        @Override
        public JsonNode getFacility(long facilityId) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", facilityId);
            return node;
        }
    }
}
