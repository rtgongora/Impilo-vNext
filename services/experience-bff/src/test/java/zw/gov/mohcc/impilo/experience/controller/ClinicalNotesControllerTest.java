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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClinicalNotesControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listNotes_returnsDataAndMeta() {
        ClinicalNotesController controller = new ClinicalNotesController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listNotes("t1", "req-1", "corr-1", 0, 20, "patient-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void getNote_returnsNoteData() {
        ClinicalNotesController controller = new ClinicalNotesController(new StubPctClient());
        UUID noteId = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response =
                controller.getNote(noteId, "t1", "req-2", "corr-2");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void createNote_returns201() {
        ClinicalNotesController controller = new ClinicalNotesController(new StubPctClient());
        ClinicalNotesController.CreateNoteRequest request =
                new ClinicalNotesController.CreateNoteRequest(
                        "patient-1", "enc-1", "SOAP", "Headache",
                        "Normal vitals", "Migraine", "Rest", "Full note body",
                        "author-1", "Dr Smith");
        ResponseEntity<Map<String, Object>> response =
                controller.createNote("t1", "pod-1", "req-3", "corr-3", null, request);
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
                null, null, null, null, null, null, null, null, null, null
        );
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode listClinicalNotes(String patientCpid, int page, int size) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "note-1").put("note_type", "SOAP"));
            return arr;
        }

        @Override public JsonNode getClinicalNote(String noteId) {
            return mapper.createObjectNode().put("id", noteId).put("note_type", "SOAP");
        }

        @Override public JsonNode createClinicalNote(Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", "note-new");
            node.put("patient_id", body.get("patient_id").toString());
            return node;
        }

        @Override public JsonNode signClinicalNote(String noteId) {
            return mapper.createObjectNode().put("id", noteId).put("signed", true);
        }
    }
}
