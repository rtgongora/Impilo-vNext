package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationTemplateControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void listReturnsTemplatesFromUpstream() {
        NotificationTemplateController controller = new NotificationTemplateController(new StubNotificationClient());
        ResponseEntity<Map<String, Object>> response = controller.list("req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
        assertEquals("corr-1", ((Map<?, ?>) response.getBody().get("meta")).get("correlation_id"));
    }

    @Test
    void publishForwardsToNotificationService() {
        NotificationTemplateController controller = new NotificationTemplateController(new StubNotificationClient());
        ResponseEntity<Map<String, Object>> response = controller.publish("tpl-1", "req-2", "corr-2");
        assertEquals(200, response.getStatusCode().value());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubNotificationClient extends NotificationServiceClient {
        StubNotificationClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode listTemplates() {
            ArrayNode arr = MAPPER.createArrayNode();
            arr.add(MAPPER.createObjectNode().put("key", "WELCOME").put("status", "PUBLISHED"));
            return arr;
        }

        @Override
        public JsonNode publishTemplate(String templateId) {
            return MAPPER.createObjectNode().put("id", templateId).put("status", "PUBLISHED");
        }
    }
}
