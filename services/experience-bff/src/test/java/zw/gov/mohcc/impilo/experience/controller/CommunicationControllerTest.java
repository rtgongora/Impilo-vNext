package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.CampaignsServiceClient;
import zw.gov.mohcc.impilo.experience.client.ChannelsServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineCommsMetricsStore;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineWorkflowHistoryStore;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunicationControllerTest {

    @Test
    void listAnnouncementsReturnsBadGatewayWhenCommunicationDependenciesUnavailable() {
        CommunicationController controller = new CommunicationController(
                new FailingCampaignsClient(),
                new SupportServiceClient(new RestTemplate(), ServiceClientConfig.testServiceEndpoints()),
                new ChannelsServiceClient(new RestTemplate(), ServiceClientConfig.testServiceEndpoints()),
                new NotificationServiceClient(new RestTemplate(), ServiceClientConfig.testServiceEndpoints()),
                new TelemedicineCommsMetricsStore(),
                new TelemedicineWorkflowHistoryStore());

        var response = controller.listAnnouncements("tenant-1", "req-1", "corr-1", null, 0, 30);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("COMMUNICATION_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void dashboardReturnsRoleSectionsEvenWhenSourcesAreUnavailable() {
        CommunicationController controller = new CommunicationController(
                new FailingCampaignsClient(),
                new FailingSupportClient(),
                new FailingChannelsClient(),
                new FailingNotificationClient(),
                new TelemedicineCommsMetricsStore(),
                new TelemedicineWorkflowHistoryStore());

        var response = controller.dashboard("req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().data().operations());
        assertNotNull(response.getBody().data().governance());
        assertTrue(response.getBody().data().governance().containsKey("source_health"));
    }

    @Test
    void healthReturnsChecksEnvelope() {
        CommunicationController controller = new CommunicationController(
                new FailingCampaignsClient(),
                new FailingSupportClient(),
                new FailingChannelsClient(),
                new FailingNotificationClient(),
                new TelemedicineCommsMetricsStore(),
                new TelemedicineWorkflowHistoryStore());
        var response = controller.health("req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().data().containsKey("checks"));
    }

    @Test
    void telemedicineOperationsEndpointReturnsConsoleBlock() {
        CommunicationController controller = new CommunicationController(
                new FailingCampaignsClient(),
                new FailingSupportClient(),
                new FailingChannelsClient(),
                new FailingNotificationClient(),
                new TelemedicineCommsMetricsStore(),
                new TelemedicineWorkflowHistoryStore());
        var response = controller.telemedicineOperationalConsole("req-1", "corr-1");
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().data().containsKey("console"));
        assertTrue(response.getBody().data().containsKey("metrics"));
    }

    @Test
    void telemedicineWorkflowRetentionAndPruneEndpointsReturnEnvelope() {
        CommunicationController controller = new CommunicationController(
                new FailingCampaignsClient(),
                new FailingSupportClient(),
                new FailingChannelsClient(),
                new FailingNotificationClient(),
                new TelemedicineCommsMetricsStore(),
                new TelemedicineWorkflowHistoryStore());

        var retention = controller.updateTelemedicineWorkflowRetention(
                "req-1",
                "corr-1",
                Map.of("max_history", 2500, "retention_hours", 96));
        assertEquals(200, retention.getStatusCode().value());
        assertNotNull(retention.getBody());
        assertTrue(retention.getBody().data().containsKey("retention"));

        var prune = controller.pruneTelemedicineWorkflowHistory("req-1", "corr-1");
        assertEquals(200, prune.getStatusCode().value());
        assertNotNull(prune.getBody());
        assertTrue(prune.getBody().data().containsKey("removed"));
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

    private static final class FailingSupportClient extends SupportServiceClient {
        private FailingSupportClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listTickets(String status, String assigneeRef, String category, String reporterRef, int page, int size) {
            throw new RuntimeException("support unavailable");
        }
    }

    private static final class FailingChannelsClient extends ChannelsServiceClient {
        private FailingChannelsClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listSessions() {
            throw new RuntimeException("channels unavailable");
        }
    }

    private static final class FailingNotificationClient extends NotificationServiceClient {
        private FailingNotificationClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode listNotifications(int page, int size) {
            throw new RuntimeException("notification unavailable");
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode unreadCount() {
            throw new RuntimeException("notification unavailable");
        }
    }
}
