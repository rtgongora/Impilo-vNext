package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.NdrQueryServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NdrControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void queryBronzeReturnsDataWhenNdrSucceeds() {
        NdrController controller = new NdrController(new SuccessNdrClient());
        var response = controller.queryBronze("tenant-1", "req-bronze", "corr-bronze", null, null, 0, 10);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
        assertTrue(response.getBody().get("data").toString().contains("bronze-evt-1"));
    }

    @Test
    void queryGoldEncountersReturnsDataWhenNdrSucceeds() {
        NdrController controller = new NdrController(new SuccessNdrClient());
        var response = controller.queryGoldEncounters(
                "tenant-1", "req-gold", "corr-gold", "patient-42", 0, 10);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
        assertTrue(response.getBody().get("data").toString().contains("enc-1"));
    }

    @Test
    void queryBronzeReturnsBadGatewayWhenNdrUnavailable() {
        NdrController controller = new NdrController(new FailingNdrClient());
        var response = controller.queryBronze("tenant-1", "req-1", "corr-1", null, null, 0, 10);
        assertEquals(502, response.getStatusCode().value());
        assertEquals("NDR_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class SuccessNdrClient extends NdrQueryServiceClient {
        private SuccessNdrClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode queryBronze(String eventType, String subjectId, int cursor, int limit) {
            ArrayNode items = MAPPER.createArrayNode();
            items.add(MAPPER.createObjectNode()
                    .put("event_id", "bronze-evt-1")
                    .put("event_type", "impilo.test.event.v1"));
            return MAPPER.createObjectNode().set("items", items);
        }

        @Override
        public JsonNode queryGoldEncounters(String patientId, int cursor, int limit) {
            ArrayNode items = MAPPER.createArrayNode();
            items.add(MAPPER.createObjectNode()
                    .put("encounter_id", "enc-1")
                    .put("patient_ref", patientId));
            return MAPPER.createObjectNode().set("items", items);
        }
    }

    private static final class FailingNdrClient extends NdrQueryServiceClient {
        private FailingNdrClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode queryBronze(String eventType, String subjectId, int cursor, int limit) {
            throw new RuntimeException("ndr unavailable");
        }
    }
}
