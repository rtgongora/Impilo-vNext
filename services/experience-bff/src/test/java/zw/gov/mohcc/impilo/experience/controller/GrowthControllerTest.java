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
import zw.gov.mohcc.impilo.experience.service.GrowthStandardsService;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GrowthControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void listGrowth_returnsDataAndMeta() {
        GrowthController controller = new GrowthController(
                new GrowthStandardsService(mapper), new StubPctClient());
        ResponseEntity<Map<String, Object>> response =
                controller.listGrowth("t1", "req-1", "corr-1", "patient-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void recordGrowth_returns201() {
        GrowthController controller = new GrowthController(
                new GrowthStandardsService(mapper), new StubPctClient());
        GrowthController.RecordGrowthRequest request =
                new GrowthController.RecordGrowthRequest(
                        "patient-1", "enc-1", "doc-1", null,
                        new BigDecimal("10.5"), null, new BigDecimal("75.0"),
                        new BigDecimal("45.0"), null, "STANDING", "Normal");
        ResponseEntity<Map<String, Object>> response =
                controller.recordGrowth("t1", "req-2", "corr-2", request);
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
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

        @Override public JsonNode listGrowthMeasurements(String patientCpid) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "gm-1"));
            return arr;
        }

        @Override public JsonNode recordGrowthMeasurement(Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", "gm-new");
            return node;
        }

        @Override public JsonNode getPatientHealthSummary(String cpid) {
            return mapper.createObjectNode();
        }
    }
}
