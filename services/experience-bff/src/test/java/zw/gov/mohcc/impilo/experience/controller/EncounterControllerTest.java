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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EncounterControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listEncounters_returnsDataAndMeta() {
        EncounterController controller = new EncounterController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listEncounters("tenant-1", "req-1", "corr-1", 0, 20, "patient-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void listEncounters_emptyPatientId_returnsBadRequest() {
        EncounterController controller = new EncounterController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listEncounters("tenant-1", "req-2", "corr-2", 0, 20, null);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("MISSING_PATIENT_ID", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void createEncounter_returns201() {
        EncounterController controller = new EncounterController(new StubPctClient());
        EncounterController.CreateEncounterRequest request = new EncounterController.CreateEncounterRequest(
                "patient-1",
                "facility-1",
                "CONSULTATION",
                "outpatient",
                "walk_in",
                "in_person",
                null,
                "facility",
                "routine",
                "P4",
                "PATH-GENERAL-01",
                "PROTO-GENERAL-01",
                "Headache",
                "journey-1",
                "cpid-1"
        );
        ResponseEntity<Map<String, Object>> response =
                controller.createEncounter("t1", "pod-1", "req-3", "corr-3", null, request);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-3", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void closeEncounter_usesPctCompletionAndReturnsOk() {
        EncounterController controller = new EncounterController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.closeEncounter("1", "tenant-1", "req-4", "corr-4", null, Map.of());
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("COMPLETED", data.get("status").asText());
    }

    @Test
    void dischargeEncounter_resolvesJourneyAndStartsDischarge() {
        EncounterController controller = new EncounterController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.dischargeEncounter("1", "tenant-1", "req-5", "corr-5", null, Map.of("dischargeType", "CLINICAL"));
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("journey-1", data.get("journeyId").asText());
        assertEquals("INITIATED", data.get("status").asText());
    }

    @Test
    void updateEncounterPathwayProtocol_returnsOk() {
        EncounterController controller = new EncounterController(new StubPctClient());
        ResponseEntity<Map<String, Object>> response = controller.updateEncounterPathwayProtocol(
                "1",
                "req-6",
                "corr-6",
                new EncounterController.UpdateEncounterPathwayProtocolRequest("PATH-SEPSIS-01", "PROTO-CRIT-01"));
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("PATH-SEPSIS-01", data.get("pathwayRef").asText());
        assertEquals("PROTO-CRIT-01", data.get("protocolRef").asText());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubPctClient extends PctServiceClient {
        StubPctClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode getPatientTimeline(String cpid) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "enc-1").put("type", "CONSULTATION"));
            return arr;
        }

        @Override public JsonNode startEncounter(String journeyId,
                                                 String encounterType,
                                                 String encounterContext,
                                                 String entryPoint,
                                                 String modality,
                                                 String virtualMode,
                                                 String careSetting,
                                                 String priority,
                                                 String triageCategory,
                                                 String pathwayRef,
                                                 String protocolRef) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", 1);
            node.put("encounterType", encounterType);
            node.put("journeyId", journeyId);
            node.put("encounterContext", encounterContext);
            node.put("entryPoint", entryPoint);
            node.put("modality", modality);
            node.put("careSetting", careSetting);
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

        @Override public JsonNode startDischarge(String journeyId, String dischargeType) {
            return mapper.createObjectNode()
                    .put("journeyId", journeyId)
                    .put("status", "INITIATED")
                    .put("dischargeType", dischargeType);
        }

        @Override public JsonNode updateEncounterPathwayProtocol(Long encounterId, String pathwayRef, String protocolRef) {
            return mapper.createObjectNode()
                    .put("id", encounterId)
                    .put("pathwayRef", pathwayRef)
                    .put("protocolRef", protocolRef);
        }
    }

}
