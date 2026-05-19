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
        items.add(mapper.createObjectNode().put("sku", "item-1"));

        MarketplaceController.CreateOrderRequest request = new MarketplaceController.CreateOrderRequest(
                "facility-1",
                "ORD-001",
                items,
                "provider-1",
                "10.00"
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
    void listOrdersReturnsTypedNotImplementedEnvelope() {
        MarketplaceController controller = new MarketplaceController(
                new FailingMsikaFlowClient(),
                new NoopMsikaServiceClient());

        ResponseEntity<Map<String, Object>> response = controller.listOrders(
                "tenant-1", "req-2", "corr-2", 0, 20, null);

        assertEquals(501, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("MARKETPLACE_ROUTE_UNAVAILABLE", ((Map<?, ?>) response.getBody().get("error")).get("code"));
        assertEquals("req-2", ((Map<?, ?>) response.getBody().get("meta")).get("request_id"));
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
