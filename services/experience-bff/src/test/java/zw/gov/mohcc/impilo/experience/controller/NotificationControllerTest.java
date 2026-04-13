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

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void list_returnsDataAndMeta() {
        NotificationController controller = new NotificationController(new StubNotificationClient());
        ResponseEntity<Map<String, Object>> response =
                controller.list(null, "req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void send_returns201() {
        NotificationController controller = new NotificationController(new StubNotificationClient());
        ResponseEntity<Map<String, Object>> response =
                controller.send("req-2", "corr-2",
                        Map.of("recipientId", "user-1", "subject", "Test", "body", "Hello"));
        assertEquals(201, response.getStatusCode().value());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void markAsRead_returnsUpdatedNotification() {
        NotificationController controller = new NotificationController(new StubNotificationClient());
        ResponseEntity<Map<String, Object>> response =
                controller.markAsRead("notif-1", "req-3", "corr-3");
        assertEquals(200, response.getStatusCode().value());
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

    private static final class StubNotificationClient extends NotificationServiceClient {
        StubNotificationClient() { super(new RestTemplate(), endpoints()); }

        @Override public JsonNode listNotifications(String recipientId) {
            ArrayNode arr = mapper.createArrayNode();
            arr.add(mapper.createObjectNode().put("id", "notif-1").put("read", false));
            return arr;
        }

        @Override public JsonNode sendNotification(Map<String, Object> body) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", "notif-new");
            node.put("status", "SENT");
            return node;
        }

        @Override public JsonNode markAsRead(String id) {
            return mapper.createObjectNode().put("id", id).put("read", true);
        }

        @Override public JsonNode getPreferences() {
            return mapper.createObjectNode().put("email", true).put("sms", false);
        }

        @Override public JsonNode updatePreferences(Map<String, Object> body) {
            return mapper.createObjectNode().put("email", true).put("sms", true);
        }
    }
}
