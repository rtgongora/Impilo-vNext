package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listReturnsBadGatewayWhenNotificationsUnavailable() {
        NotificationController controller = new NotificationController(new FailingNotificationClient());

        var response = controller.list(null, null, "req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("NOTIFICATIONS_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void sendReturns201WithRealEnvelopeWhenUpstreamSucceeds() {
        NotificationController controller = new NotificationController(new StubNotificationClient());

        ResponseEntity<Map<String, Object>> response =
                controller.send("req-2", "corr-2", Map.of("recipientId", "user-1", "subject", "Test", "body", "Hello"));

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubNotificationClient extends NotificationServiceClient {
        private StubNotificationClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listNotifications(String recipientId) {
            ArrayNode arr = MAPPER.createArrayNode();
            arr.add(MAPPER.createObjectNode().put("id", "notif-1").put("read", false));
            return arr;
        }

        @Override
        public JsonNode sendNotification(Map<String, Object> body) {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", "notif-new");
            node.put("status", "SENT");
            return node;
        }
    }

    private static final class FailingNotificationClient extends NotificationServiceClient {
        private FailingNotificationClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listNotifications(String recipientId) {
            throw new RuntimeException("notifications unavailable");
        }
    }
}
