package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.UbomiServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UbomiControllerTest {

    @Test
    void status_returnsAvailabilityEnvelope() {
        UbomiController controller = new UbomiController(new StubUbomiClient());
        ResponseEntity<Map<String, Object>> response = controller.status("req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertTrue((Boolean) data.get("available"));
    }

    private static final class StubUbomiClient extends UbomiServiceClient {
        private static final ObjectMapper mapper = new ObjectMapper();

        StubUbomiClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode listBirthNotifications(int page, int size) {
            return mapper.createArrayNode();
        }
    }
}
