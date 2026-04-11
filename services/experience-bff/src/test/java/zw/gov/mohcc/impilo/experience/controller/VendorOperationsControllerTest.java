package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VendorOperationsControllerTest {

    @Test
    void listVendorOrdersForwardsPayloadWithMetadata() {
        VendorOperationsController controller = new VendorOperationsController(new StubFlowClient());
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("status", "ROUTED");

        ResponseEntity<String> response = controller.getVendorOrders("vendor-1", query, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/json", response.getHeaders().getContentType().toString());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"content\":[{\"orderId\":\"ORDER-001\",\"status\":\"ROUTED\"}]}", response.getBody());
    }

    @Test
    void markReadyForwardsVendorFulfillmentAction() {
        VendorOperationsController controller = new VendorOperationsController(new StubFlowClient());

        ResponseEntity<String> response = controller.markReady("ORDER-001", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"orderId\":\"ORDER-001\",\"status\":\"READY\"}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                "http://pct",
                "http://oros",
                "http://pharmacy",
                "http://butano",
                "http://msika",
                "http://msika-flow",
                "http://mushex",
                "http://vito",
                "http://tuso",
                "http://varapi",
                "http://documents",
                "http://costa",
                "http://coverage",
                "http://surveillance",
                "http://campaigns",
                "http://indawo",
                "http://governance",
                "http://landela",
                "http://notifications"
        );
    }

    private static final class StubFlowClient extends MsikaFlowServiceClient {
        private StubFlowClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> getVendorOrders(String vendorId, MultiValueMap<String, String> queryParams) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"content\":[{\"orderId\":\"ORDER-001\",\"status\":\"ROUTED\"}]}");
        }

        @Override
        public ResponseEntity<String> markOrderReady(String orderId) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"orderId\":\"" + orderId + "\",\"status\":\"READY\"}");
        }
    }
}
