package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EncounterControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listEncounters_returnsDataAndMeta() {
        EncounterController controller = new EncounterController(new StubPctClient(), new StubCostaClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listEncounters("t1", "req-1", "corr-1", 0, 20, "patient-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listEncounters_emptyPatientId_returnsEmptyData() {
        EncounterController controller = new EncounterController(new StubPctClient(), new StubCostaClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listEncounters("t1", "req-2", "corr-2", 0, 20, null);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void createEncounter_returns201() {
        EncounterController controller = new EncounterController(new StubPctClient(), new StubCostaClient());
        EncounterController.CreateEncounterRequest request =
                new EncounterController.CreateEncounterRequest(
                        "patient-1", "facility-1", "CONSULTATION",
                        "Headache", "journey-1", "cpid-1");
        ResponseEntity<Map<String, Object>> response =
                controller.createEncounter("t1", "pod-1", "req-3", "corr-3", null, request);
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
                null, null, null, null, null, null, null, null, null
        );
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode getPatientTimeline(String cpid) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "enc-1").put("type", "CONSULTATION"));
            return arr;
        }

        @Override public JsonNode startEncounter(String journeyId, String encounterType) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", 1);
            node.put("encounterType", encounterType);
            node.put("journeyId", journeyId);
            return node;
        }

        @Override public JsonNode completeEncounter(Long encounterId) {
            return mapper.createObjectNode().put("id", encounterId).put("status", "COMPLETED");
        }

        @Override public JsonNode getEncounter(long encounterId) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", encounterId);
            node.put("journeyId", "journey-1");
            return node;
        }
    }

    private static final class StubCostaClient extends CostaServiceClient {
        StubCostaClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode createBillDraft(String encounterId, String billType) {
            ObjectNode node = mapper.createObjectNode();
            node.put("billId", "bill-1");
            return node;
        }
    }
}
