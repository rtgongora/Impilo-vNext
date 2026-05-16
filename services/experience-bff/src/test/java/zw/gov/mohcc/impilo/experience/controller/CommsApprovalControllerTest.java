package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CampaignsServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommsApprovalControllerTest {

    @Test
    void listQueueReturnsHealthEvenWhenUpstreamsUnavailable() {
        CommsApprovalController controller = new CommsApprovalController(
                new FailingNotificationClient(),
                new FailingCampaignsClient());

        var response = controller.listQueue("req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().data());
        assertNotNull(response.getBody().data().source_health());
    }

    @Test
    void rejectRequiresDecisionReason() {
        CommsApprovalController controller = new CommsApprovalController(
                new FailingNotificationClient(),
                new FailingCampaignsClient());
        var response = controller.rejectCampaignWithReason(123L, "reviewer-1", "req-1", "corr-1", Map.of());
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("VALIDATION_FAILED"));
    }

    @Test
    void historyEndpointReturnsDataEnvelope() {
        CommsApprovalController controller = new CommsApprovalController(
                new FailingNotificationClient(),
                new FailingCampaignsClient());
        var response = controller.listApprovalHistory("req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().data().containsKey("items"));
    }

    @Test
    void dryRunEndpointsReturnEvaluationPayload() {
        CommsApprovalController controller = new CommsApprovalController(
                new FailingNotificationClient(),
                new FailingCampaignsClient());

        var campaignDryRun = controller.dryRunCampaign(123L, "req-1", "corr-1");
        assertEquals(200, campaignDryRun.getStatusCode().value());
        assertNotNull(campaignDryRun.getBody());
        assertTrue(campaignDryRun.getBody().toString().contains("dry_run_ok"));

        var templateDryRun = controller.dryRunTemplate("template-1", "req-1", "corr-1");
        assertEquals(200, templateDryRun.getStatusCode().value());
        assertNotNull(templateDryRun.getBody());
        assertTrue(templateDryRun.getBody().toString().contains("dry_run_ok"));
    }

    @Test
    void healthEndpointReturnsChecksEnvelope() {
        CommsApprovalController controller = new CommsApprovalController(
                new FailingNotificationClient(),
                new FailingCampaignsClient());
        var response = controller.health("req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().data().containsKey("checks"));
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

    private static final class FailingCampaignsClient extends CampaignsServiceClient {
        private FailingCampaignsClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listCampaigns(int page, int size) {
            throw new RuntimeException("campaigns unavailable");
        }
    }
}
