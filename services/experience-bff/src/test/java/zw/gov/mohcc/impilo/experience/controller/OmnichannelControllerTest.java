package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CampaignsServiceClient;
import zw.gov.mohcc.impilo.experience.client.IntegrationHubServiceClient;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.resilience.UpstreamSourceCircuitBreaker;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmnichannelControllerTest {

    @Test
    void listCallbacksReturnsBadGatewayWhenOmnichannelDependenciesUnavailable() {
        OmnichannelController controller = new OmnichannelController(
                new FailingSupportClient(),
                new CampaignsServiceClient(new RestTemplate(), ServiceClientConfig.testServiceEndpoints()),
                new IntegrationHubServiceClient(new RestTemplate(), ServiceClientConfig.testServiceEndpoints()),
                new UpstreamSourceCircuitBreaker());

        var response = controller.listCallbacks("tenant-1", "req-1", null);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("OMNICHANNEL_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void dashboardReturnsMetricsEvenWhenSourcesAreUnavailable() {
        OmnichannelController controller = new OmnichannelController(
                new FailingSupportClient(),
                new FailingCampaignsClient(),
                new FailingIntegrationHubClient(),
                new UpstreamSourceCircuitBreaker());

        var response = controller.dashboard("tenant-1", "req-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().data());
        assertTrue(response.getBody().data().governance().containsKey("source_health"));
    }

    @Test
    void healthReturnsChecksEnvelope() {
        OmnichannelController controller = new OmnichannelController(
                new FailingSupportClient(),
                new FailingCampaignsClient(),
                new FailingIntegrationHubClient(),
                new UpstreamSourceCircuitBreaker());
        var response = controller.health("req-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().data().containsKey("checks"));
    }

    private static final class FailingSupportClient extends SupportServiceClient {
        private FailingSupportClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listTickets(String status) {
            throw new RuntimeException("support unavailable");
        }
    }

    private static final class FailingCampaignsClient extends CampaignsServiceClient {
        private FailingCampaignsClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listCampaigns(int page, int size) {
            throw new RuntimeException("campaigns unavailable");
        }
    }

    private static final class FailingIntegrationHubClient extends IntegrationHubServiceClient {
        private FailingIntegrationHubClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listRoutes() {
            throw new RuntimeException("routes unavailable");
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listMappingTemplates() {
            throw new RuntimeException("mapping templates unavailable");
        }
    }
}
