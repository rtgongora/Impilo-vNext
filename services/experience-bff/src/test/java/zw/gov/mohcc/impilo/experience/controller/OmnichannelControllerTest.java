package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OmnichannelControllerTest {

    @Test
    void listCallbacksReturnsBadGatewayWhenCommunityUnavailable() {
        OmnichannelController controller = new OmnichannelController(new FailingCommunityClient());

        var response = controller.listCallbacks("tenant-1", "req-1", null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMUNITY_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class FailingCommunityClient extends CommunityServiceClient {
        private FailingCommunityClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listVisits(String unitId) {
            throw new RuntimeException("community unavailable");
        }
    }
}
