package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClinicalDocumentsControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listDocuments_returnsDataAndMeta() {
        ClinicalDocumentsController controller =
                new ClinicalDocumentsController(new StubPctClient(), new StubDocumentClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listDocuments("t1", "req-1", "corr-1", 0, 20, "patient-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));

        // The composed row: JSON:API envelope, both dialects inside attributes — the read hooks
        // type camelCase (documentType/createdAt) while the create contract is snake_case.
        var rows = (java.util.List<?>) response.getBody().get("data");
        assertEquals(1, rows.size());
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals("rec-1", row.get("id"));
        assertEquals("clinical_document", row.get("type"));
        Map<?, ?> attributes = (Map<?, ?>) row.get("attributes");
        assertEquals("LAB_REPORT", attributes.get("documentType"));
        assertEquals("LAB_REPORT", attributes.get("document_type"));
        assertNotNull(attributes.get("createdAt"));
    }

    @Test
    void listDocuments_emptyPatientId_returnsEmptyData() {
        ClinicalDocumentsController controller =
                new ClinicalDocumentsController(new StubPctClient(), new StubDocumentClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listDocuments("t1", "req-2", "corr-2", 0, 20, null);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void uploadAndIndex_chainsDocumentStoreToPctRecord() throws Exception {
        ClinicalDocumentsController controller =
                new ClinicalDocumentsController(new StubPctClient(), new UploadingStubDocumentClient());
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "clinical-bytes".getBytes());
        ResponseEntity<Map<String, Object>> response = controller.uploadAndIndex(
                "t1", "pod-1", "req-up", "corr-up",
                file, "patient-1", "CLINICAL_NOTE", "Referral scan",
                "OCR indexed", "enc-1", "Dr. Moyo");
        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
    }

    @Test
    void createDocument_returns201() {
        ClinicalDocumentsController controller =
                new ClinicalDocumentsController(new StubPctClient(), new StubDocumentClient());
        ClinicalDocumentsController.CreateDocumentRequest request =
                new ClinicalDocumentsController.CreateDocumentRequest(
                        "patient-1", "enc-1", "LAB_REPORT", "Blood Test",
                        "Description", "application/pdf", 1024L, "key-1", "doc-1", null);
        ResponseEntity<Map<String, Object>> response =
                controller.createDocument("t1", "pod-1", "req-3", "corr-3", null, request);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-3", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode getPatientRecords(String cpid, String documentType) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode()
                    .put("document_id", "rec-1")
                    .put("patient_id", cpid)
                    .put("document_type", "LAB_REPORT")
                    .put("title", "FBC")
                    .put("storage_key", "key-1")
                    .put("recorded_at", "2026-07-26T10:00:00+02:00")
                    .put("status", "ACTIVE"));
            return arr;
        }

        @Override public JsonNode createPatientRecord(Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("document_id", "rec-new");
            node.put("title", body.get("title").toString());
            node.put("document_type", String.valueOf(body.get("document_type")));
            return node;
        }
    }

    private static final class StubDocumentClient extends DocumentServiceClient {
        StubDocumentClient() { super(new RestTemplate(), endpoints()); }
    }

    private static final class UploadingStubDocumentClient extends DocumentServiceClient {
        UploadingStubDocumentClient() { super(new RestTemplate(), endpoints()); }

        @Override
        public JsonNode uploadObject(byte[] bytes, String originalFilename, String mimeType) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", "00000000-0000-0000-0000-000000000099");
            node.put("storageKey", "uploads/clinical/test.pdf");
            return node;
        }
    }
}
