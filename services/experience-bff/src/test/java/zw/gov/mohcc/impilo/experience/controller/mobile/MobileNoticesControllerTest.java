package zw.gov.mohcc.impilo.experience.controller.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MobileNoticesControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void returnsBadGatewayWhenVarapiUnavailable() {
        MobileNoticesController controller = new MobileNoticesController(new FailingVarapiClient());

        ResponseEntity<Map<String, Object>> response = controller.listNotices(
                "tenant-1", "req-1", "corr-1", "actor-1", 0, 20);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("VARAPI_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void returnsRealNoticesPayload() {
        MobileNoticesController controller = new MobileNoticesController(new SuccessVarapiClient());

        ResponseEntity<Map<String, Object>> response = controller.listNotices(
                "tenant-1", "req-2", "corr-2", "actor-2", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("data"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingVarapiClient extends VarapiServiceClient {
        private FailingVarapiClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getProviderNotices(String healthId) {
            throw new RuntimeException("varapi unavailable");
        }
    }

    private static final class SuccessVarapiClient extends VarapiServiceClient {
        private SuccessVarapiClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public JsonNode getProviderNotices(String healthId) {
            try {
                return MAPPER.readTree("""
                        [{"id":"notice-1","title":"License expires soon"}]
                        """);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
