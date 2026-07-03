package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MarketplaceControllerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createOrderReturnsTypedBadGatewayWhenMsikaFlowUnavailable() {
        MarketplaceController controller = new MarketplaceController(
                new FailingMsikaFlowClient(),
                new NoopMsikaServiceClient());

        ArrayNode items = mapper.createArrayNode();
        items.add(mapper.createObjectNode().put("productId", "item-1").put("quantity", 2).put("unitPrice", 5));

        MarketplaceController.CreateOrderRequest request = new MarketplaceController.CreateOrderRequest(
                "facility-1",
                null,
                items,
                "provider-1"
        );

        ResponseEntity<Map<String, Object>> response = controller.createOrder(
                "tenant-1", "pod-1", "req-1", "corr-1", "idem-1", request);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("MSIKA_FLOW_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals("req-1", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
        assertEquals("corr-1", ((Map<?, ?>) response.getBody().get("meta")).get("correlation_id"));
    }

    @Test
    void createOrderTranslatesToMsikaFlowContractAndNormalisesResponse() {
        CapturingMsikaFlowClient msikaFlow = new CapturingMsikaFlowClient();
        MarketplaceController controller = new MarketplaceController(msikaFlow, new NoopMsikaServiceClient());

        ArrayNode items = mapper.createArrayNode();
        items.add(mapper.createObjectNode()
                .put("productId", "MSK-001").put("description", "Gauze").put("quantity", 3).put("unitPrice", 2.5));

        ResponseEntity<Map<String, Object>> response = controller.createOrder(
                "tenant-1", "pod-1", "req-1", "corr-1", "idem-1",
                new MarketplaceController.CreateOrderRequest("facility-1", null, items, "provider-1"));

        assertEquals(201, response.getStatusCode().value());
        // Downstream body must match msika-flow's canonical CreateOrderRequest.
        assertNotNull(msikaFlow.lastBody);
        assertEquals(true, msikaFlow.lastBody.contains("\"orderType\":\"OTC_PRODUCT_ORDER\""));
        assertEquals(true, msikaFlow.lastBody.contains("\"msikaCoreCode\":\"MSK-001\""));
        assertEquals(true, msikaFlow.lastBody.contains("\"qty\":3"));
        assertEquals(true, msikaFlow.lastBody.contains("\"idempotencyKey\":\"idem-1\""));
        // metadata must be a JSON object string, not bare text (json column downstream).
        assertEquals(true, msikaFlow.lastBody.contains("\"metadata\":\"{\\\"description\\\":\\\"Gauze\\\"}\""));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("ord-123", data.get("id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        // Order identity comes from the server, never fabricated client-side.
        assertEquals("ord-123", attributes.get("order_number"));
        assertEquals("CREATED", attributes.get("status"));
    }

    @Test
    void createOrderRejectsUnknownOrderType() {
        MarketplaceController controller = new MarketplaceController(
                new CapturingMsikaFlowClient(), new NoopMsikaServiceClient());

        ResponseEntity<Map<String, Object>> response = controller.createOrder(
                "tenant-1", "pod-1", "req-1", "corr-1", null,
                new MarketplaceController.CreateOrderRequest("facility-1", "NOT_A_TYPE", null, null));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_ORDER_TYPE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void listOrdersNormalisesUpstreamOrderViews() {
        MarketplaceController controller = new MarketplaceController(
                new ListingMsikaFlowClient(), new NoopMsikaServiceClient());

        ResponseEntity<Map<String, Object>> response = controller.listOrders(
                "tenant-1", "req-2", "corr-2", 0, 20, "facility-1");

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> data =
                (java.util.List<Map<String, Object>>) response.getBody().get("data");
        assertEquals(1, data.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) data.get(0).get("attributes");
        assertEquals("ord-9", attributes.get("order_number"));
        assertEquals("facility-1", attributes.get("facility_id"));
    }

    @Test
    void listOrdersReturnsUpstreamEnvelope() {
        MarketplaceController controller = new MarketplaceController(
                new ListingMsikaFlowClient(),
                new NoopMsikaServiceClient());

        ResponseEntity<Map<String, Object>> response = controller.listOrders(
                "tenant-1", "req-2", "corr-2", 0, 20, "facility-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
    }

    private static final class CapturingMsikaFlowClient extends MsikaFlowServiceClient {
        String lastBody;

        CapturingMsikaFlowClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> createOrder(String requestBody) {
            this.lastBody = requestBody;
            return ResponseEntity.status(201).body(
                    "{\"success\":true,\"data\":{\"orderId\":\"ord-123\",\"status\":\"CREATED\"," +
                    "\"facilityId\":\"facility-1\",\"amountTotal\":7.50,\"currency\":\"USD\"," +
                    "\"actorId\":\"provider-1\",\"createdAt\":\"2026-07-02T00:00:00Z\"," +
                    "\"lines\":[{\"msikaCoreCode\":\"MSK-001\",\"metadata\":\"Gauze\",\"qty\":3,\"unitPrice\":2.5}]}}");
        }
    }

    @Test
    void listCatalogPreservesUpstreamNonSuccessStatus() {
        MarketplaceController controller = new MarketplaceController(
                new FailingMsikaFlowClient(),
                new NotFoundMsikaServiceClient());

        ResponseEntity<Map<String, Object>> response = controller.listCatalog(
                "tenant-1", "req-3", "corr-3", null, "aspirin");

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("{\"error\":\"not found\"}", response.getBody().get("data"));
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class FailingMsikaFlowClient extends MsikaFlowServiceClient {
        FailingMsikaFlowClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> createOrder(String requestBody) {
            throw new RuntimeException("msika-flow unavailable");
        }
    }

    private static final class ListingMsikaFlowClient extends MsikaFlowServiceClient {
        ListingMsikaFlowClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> listOrders(int page, int size, String facilityId) {
            return ResponseEntity.ok(
                    "{\"success\":true,\"data\":{\"items\":[{\"orderId\":\"ord-9\",\"status\":\"CREATED\"," +
                    "\"facilityId\":\"facility-1\",\"amountTotal\":10,\"currency\":\"USD\",\"actorId\":\"provider-1\"," +
                    "\"createdAt\":\"2026-07-02T00:00:00Z\",\"lines\":[]}],\"page\":0,\"size\":20,\"total_elements\":1}}");
        }
    }

    private static final class NoopMsikaServiceClient extends MsikaServiceClient {
        NoopMsikaServiceClient() {
            super(new RestTemplate(), endpoints());
        }
    }

    private static final class NotFoundMsikaServiceClient extends MsikaServiceClient {
        NotFoundMsikaServiceClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> search(org.springframework.util.MultiValueMap<String, String> queryParams) {
            return ResponseEntity.status(404).body("{\"error\":\"not found\"}");
        }
    }
}
