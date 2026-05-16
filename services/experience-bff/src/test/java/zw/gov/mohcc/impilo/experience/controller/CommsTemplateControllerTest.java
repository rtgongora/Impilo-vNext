package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommsTemplateControllerTest {

    @Test
    void listTemplatesReturnsBadGatewayWhenNotificationServiceUnavailable() {
        CommsTemplateController controller = new CommsTemplateController(new FailingNotificationClient());

        var response = controller.listTemplates("req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMS_TEMPLATE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class FailingNotificationClient extends NotificationServiceClient {
        private FailingNotificationClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listTemplates() {
            throw new RuntimeException("notification unavailable");
        }
    }
}
