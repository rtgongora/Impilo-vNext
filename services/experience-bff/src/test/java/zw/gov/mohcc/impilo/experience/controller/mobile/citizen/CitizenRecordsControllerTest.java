package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The citizen records surface: the caller's own actor id is the subject binding (a person can
 * only list their own documents), the rows are the flat {@code MedicalRecord} shape, and
 * {@code meta.page} carries real numbers — the app reads {@code total_elements} unguarded.
 */
class CitizenRecordsControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubPctClient extends PctServiceClient {
        String lastSubject;
        String lastType;

        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode getPatientRecords(String cpid, String documentType) {
            lastSubject = cpid;
            lastType = documentType;
            ArrayNode arr = mapper.createArrayNode();
            for (int i = 0; i < 3; i++) {
                arr.add(mapper.createObjectNode()
                        .put("document_id", "doc-" + i)
                        .put("patient_id", cpid)
                        .put("document_type", "LAB_REPORT")
                        .put("title", "FBC " + i)
                        .put("description", "full blood count")
                        .put("uploaded_by", "Dr. Moyo")
                        .put("recorded_at", "2026-07-2" + i + "T10:00:00+02:00")
                        .put("status", "ACTIVE"));
            }
            return arr;
        }
    }

    private static final class StubDocumentClient extends DocumentServiceClient {
        StubDocumentClient() { super(new RestTemplate(), endpoints()); }
    }

    @Test
    void listBindsTheCallerToTheirOwnRecord_andPagesHonestly() {
        StubPctClient pct = new StubPctClient();
        CitizenRecordsController controller = new CitizenRecordsController(pct, new StubDocumentClient());

        ResponseEntity<Map<String, Object>> response =
                controller.list("t1", "req-1", "corr-1", "CPID-ME", null, "LAB_REPORT", 0, 2);

        assertEquals(200, response.getStatusCode().value());
        // The subject pct was asked about is the caller — never a client-supplied patient id.
        assertEquals("CPID-ME", pct.lastSubject);
        assertEquals("LAB_REPORT", pct.lastType);

        List<?> rows = (List<?>) response.getBody().get("data");
        assertEquals(2, rows.size(), "page size 2 of 3 rows");
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertEquals("doc-0", row.get("id"));
        assertEquals("FBC 0", row.get("title"));
        assertEquals("LAB_REPORT", row.get("type"));
        assertEquals("full blood count", row.get("summary"));
        assertNotNull(row.get("createdAt"));
        assertFalse(row.containsKey("attributes"), "citizen rows are flat, not JSON:API");

        // recordsService reads meta.page.total_elements unguarded — it must exist and be real.
        Map<?, ?> page = (Map<?, ?>) ((Map<?, ?>) response.getBody().get("meta")).get("page");
        assertEquals(3, page.get("total_elements"));
        assertEquals(2, page.get("total_pages"));
    }

    @Test
    void anUnreachablePctIsNotAnEmptyRecordList() {
        PctServiceClient failing = new PctServiceClient(new RestTemplate(), endpoints(), mapper) {
            @Override public JsonNode getPatientRecords(String cpid, String documentType) {
                throw new IllegalStateException("connection refused");
            }
        };
        ResponseEntity<Map<String, Object>> response =
                new CitizenRecordsController(failing, new StubDocumentClient())
                        .list("t1", "req-2", "corr-2", "CPID-ME", null, null, 0, 20);
        assertEquals(502, response.getStatusCode().value());
        assertEquals("records_unavailable", response.getBody().get("error"));
        assertFalse(response.getBody().containsKey("data"));
    }
}
