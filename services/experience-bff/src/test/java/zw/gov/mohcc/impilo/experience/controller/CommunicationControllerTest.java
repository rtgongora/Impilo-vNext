package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CommunicationControllerTest {

    @Test
    void listAnnouncementsReturnsBadGatewayWhenCommunityUnavailable() {
        CommunicationController controller = new CommunicationController(new FailingCommunityClient());

        var response = controller.listAnnouncements("tenant-1", "req-1", "corr-1", null, 0, 30);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMUNITY_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    private static final class FailingCommunityClient extends CommunityServiceClient {
        private FailingCommunityClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints(), new com.fasterxml.jackson.databind.ObjectMapper());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listUnits() {
            throw new RuntimeException("community unavailable");
        }
    }
}
