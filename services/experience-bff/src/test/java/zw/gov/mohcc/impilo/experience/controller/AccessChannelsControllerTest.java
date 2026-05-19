package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AccessChannelsControllerTest {

    @Test
    void listTemplatesReturnsBadGatewayWhenUpstreamUnavailable() {
        AccessChannelsController controller = new AccessChannelsController(
                new ThrowingRestTemplate(),
                ServiceClientConfig.testServiceEndpoints());

        var response = controller.listTemplates("req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("ACCESS_CHANNEL_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class ThrowingRestTemplate extends RestTemplate {
        @Override
        public <T> org.springframework.http.ResponseEntity<T> getForEntity(String url, Class<T> responseType, Object... uriVariables) {
            throw new RuntimeException("upstream unavailable");
        }
    }
}
