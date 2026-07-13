package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BedControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listBeds_returnsDataAndMeta() {
        BedController controller = new BedController(new StubInpatientClient());
        UUID facilityId = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response =
                controller.listBeds("t1", "req-1", "corr-1", facilityId, null, null);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void updateBedStatus_returnsUpdatedStatus() {
        BedController controller = new BedController(new StubInpatientClient());
        UUID bedId = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response =
                controller.updateBedStatus(bedId, "t1", "req-2", "corr-2",
                        Map.of("status", "CLEANING"));
        assertEquals(200, response.getStatusCode().value());
        // The BFF now returns the REAL upstream bed state, not a fabricated status.
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("CLEANING", data.get("status").asText());
    }

    @Test
    void dischargeBed_returnsCleaningStatus() {
        BedController controller = new BedController(new StubInpatientClient());
        UUID bedId = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response =
                controller.dischargeBed(bedId, "t1", "req-3", "corr-3");
        assertEquals(200, response.getStatusCode().value());
        JsonNode data = (JsonNode) response.getBody().get("data");
        assertEquals("CLEANING", data.get("status").asText());
    }

    @Test
    void assignPatient_passesBedSafety409Through() {
        // A bed-safety violation must reach the caller as 409 with the message —
        // never be masked as a fabricated 200 {status: OCCUPIED}.
        BedController controller = new BedController(new UnsafeBedClient());
        UUID bedId = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response =
                controller.assignPatient(bedId, "t1", "req-4", "corr-4",
                        Map.of("patientId", "CPID-1"));
        assertEquals(409, response.getStatusCode().value());
        Map<?, ?> error = (Map<?, ?>) response.getBody().get("error");
        assertEquals("Bed assignment unsafe: GENDER_BAY_MISMATCH", error.get("message"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class UnsafeBedClient extends InpatientServiceClient {
        UnsafeBedClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode assignPatientToBed(String bedId, Map<String, Object> request) {
            byte[] body = "{\"error\":{\"code\":\"BED_UNSAFE\",\"message\":\"Bed assignment unsafe: GENDER_BAY_MISMATCH\"}}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            throw org.springframework.web.client.HttpClientErrorException.create(
                    org.springframework.http.HttpStatus.CONFLICT, "Conflict",
                    org.springframework.http.HttpHeaders.EMPTY, body, null);
        }
    }

    private static final class StubInpatientClient extends InpatientServiceClient {
        StubInpatientClient() { super(new RestTemplate(), endpoints(), mapper); }

        @Override public JsonNode listBeds(String facilityId, String wardId, String status) {
            ArrayNode arr = mapper.createArrayNode();
            ObjectNode bed = mapper.createObjectNode();
            bed.put("id", UUID.randomUUID().toString());
            bed.put("status", "AVAILABLE");
            arr.add(bed);
            return arr;
        }

        @Override public JsonNode updateBedStatus(String bedId, Map<String, Object> request) {
            return mapper.createObjectNode().put("id", bedId).put("status", "CLEANING");
        }

        @Override public JsonNode dischargeBed(String bedId) {
            return mapper.createObjectNode().put("id", bedId).put("status", "CLEANING");
        }
    }
}
