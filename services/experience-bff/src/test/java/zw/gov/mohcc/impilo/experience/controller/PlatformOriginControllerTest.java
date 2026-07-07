package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.PlatformOriginGovernanceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlatformOriginControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listCountryOperationsReturnsBadGatewayWhenGovernanceUnavailable() {
        PlatformOriginController controller = new PlatformOriginController(new FailingClient());

        var response = controller.listCountryOperations("req-1", "corr-1");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PLATFORM_ORIGIN_UNAVAILABLE",
                ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void initiateCountryOperationReturnsBadGatewayWhenGovernanceUnavailable() {
        PlatformOriginController controller = new PlatformOriginController(new FailingClient());

        var response = controller.initiateCountryOperation(
                Map.of("tenantId", UUID.randomUUID().toString(), "isoCountryCode", "ZW"),
                "req-2", "corr-2");

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PLATFORM_ORIGIN_UNAVAILABLE",
                ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void approveAndExecuteFailClosedWhenGovernanceUnavailable() {
        PlatformOriginController controller = new PlatformOriginController(new FailingClient());
        UUID actionId = UUID.randomUUID();

        assertEquals(502, controller.approve(actionId, Map.of(), "req-3", "corr-3")
                .getStatusCode().value());
        assertEquals(502, controller.execute(actionId, "req-4", "corr-4")
                .getStatusCode().value());
        assertEquals(502, controller.initiateRevocation(UUID.randomUUID(), UUID.randomUUID(),
                null, "req-5", "corr-5").getStatusCode().value());
    }

    @Test
    void successfulProxyWrapsDataWithMeta() throws Exception {
        JsonNode data = objectMapper.readTree("[{\"isoCountryCode\":\"ZW\"}]");
        PlatformOriginController controller = new PlatformOriginController(new StubClient(data));

        var response = controller.listCountryOperations("req-6", "corr-6");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(data, response.getBody().get("data"));
        assertEquals("req-6", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    @Test
    void pendingActionsAndApprovalsFailClosedWhenGovernanceUnavailable() {
        PlatformOriginController controller = new PlatformOriginController(new FailingClient());

        assertEquals(502, controller.listPendingActions("PENDING", "req-7", "corr-7")
                .getStatusCode().value());
        assertEquals(502, controller.listActionApprovals(UUID.randomUUID(), "req-8", "corr-8")
                .getStatusCode().value());
    }

    @Test
    void pendingActionsInboxProxiesFilteredList() throws Exception {
        JsonNode data = objectMapper.readTree(
                "[{\"requestType\":\"PLATFORM_ACTION\",\"status\":\"PENDING\"}]");
        PlatformOriginController controller = new PlatformOriginController(new StubClient(data));

        var response = controller.listPendingActions("PENDING", "req-9", "corr-9");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(data, response.getBody().get("data"));
    }

    @Test
    void actionApprovalsProxyWrapsWhoApprovedProjection() throws Exception {
        JsonNode data = objectMapper.readTree(
                "[{\"approverUserId\":\"approver-1\",\"approverRole\":\"PLATFORM_ORIGIN_ADMINISTRATOR\",\"decision\":\"APPROVE\"}]");
        PlatformOriginController controller = new PlatformOriginController(new StubClient(data));

        var response = controller.listActionApprovals(UUID.randomUUID(), "req-10", "corr-10");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(data, response.getBody().get("data"));
    }

    @Test
    void getActionProxiesDurableTerminalState() throws Exception {
        JsonNode data = objectMapper.readTree(
                "{\"accessRequestId\":\"a1\",\"status\":\"EXECUTED\",\"approvalsRequired\":2,\"approvalsSatisfied\":true}");
        PlatformOriginController controller = new PlatformOriginController(new StubClient(data));

        var response = controller.getAction(UUID.randomUUID(), "req-11", "corr-11");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(data, response.getBody().get("data"));
    }

    @Test
    void getActionFailsClosedWhenGovernanceUnavailable() {
        PlatformOriginController controller = new PlatformOriginController(new FailingClient());

        assertEquals(502, controller.getAction(UUID.randomUUID(), "req-12", "corr-12")
                .getStatusCode().value());
    }

    private static final class FailingClient extends PlatformOriginGovernanceClient {
        private FailingClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
        }

        @Override
        public JsonNode listCountryOperations() {
            throw new IllegalStateException("governance unavailable");
        }

        @Override
        public JsonNode initiateCountryOperation(Map<String, Object> body) {
            throw new IllegalStateException("governance unavailable");
        }

        @Override
        public JsonNode approveAction(UUID accessRequestId, Map<String, Object> body) {
            throw new IllegalStateException("governance unavailable");
        }

        @Override
        public JsonNode executeAction(UUID accessRequestId) {
            throw new IllegalStateException("governance unavailable");
        }

        @Override
        public JsonNode initiateRevocation(UUID countryOperationId, UUID appointmentId,
                                           Map<String, Object> body) {
            throw new IllegalStateException("governance unavailable");
        }

        @Override
        public JsonNode listPendingActions(String status) {
            throw new IllegalStateException("governance unavailable");
        }

        @Override
        public JsonNode getActionApprovals(UUID accessRequestId) {
            throw new IllegalStateException("governance unavailable");
        }

        @Override
        public JsonNode getAction(UUID accessRequestId) {
            throw new IllegalStateException("governance unavailable");
        }
    }

    private static final class StubClient extends PlatformOriginGovernanceClient {
        private final JsonNode data;

        private StubClient(JsonNode data) {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints());
            this.data = data;
        }

        @Override
        public JsonNode listCountryOperations() {
            return data;
        }

        @Override
        public JsonNode listPendingActions(String status) {
            return data;
        }

        @Override
        public JsonNode getActionApprovals(UUID accessRequestId) {
            return data;
        }

        @Override
        public JsonNode getAction(UUID accessRequestId) {
            return data;
        }
    }
}
