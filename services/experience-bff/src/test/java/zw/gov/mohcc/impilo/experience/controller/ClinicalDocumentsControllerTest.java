package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
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

        @Override public JsonNode getPatientRecords(String cpid, String documentType, int page, int size) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "rec-1").put("type", "LAB_REPORT"));
            return arr;
        }

        @Override public JsonNode createPatientRecord(Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", "rec-new");
            node.put("title", body.get("title").toString());
            return node;
        }
    }

    private static final class StubDocumentClient extends DocumentServiceClient {
        StubDocumentClient() { super(new RestTemplate(), endpoints()); }
    }
}
