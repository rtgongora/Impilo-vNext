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
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("CLEANING", data.get("status"));
    }

    @Test
    void dischargeBed_returnsCleaningStatus() {
        BedController controller = new BedController(new StubInpatientClient());
        UUID bedId = UUID.randomUUID();
        ResponseEntity<Map<String, Object>> response =
                controller.dischargeBed(bedId, "t1", "req-3", "corr-3");
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("CLEANING", data.get("status"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
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
