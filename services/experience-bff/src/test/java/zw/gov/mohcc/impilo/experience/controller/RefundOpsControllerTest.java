package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RefundOpsControllerTest {

    @Test
    void getIntentForRefundWorkspaceForwardsPayload() {
        RefundOpsController controller = new RefundOpsController(new StubMushexClient());

        ResponseEntity<String> response = controller.getPaymentIntent("PI-1", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("{\"intentId\":\"PI-1\",\"status\":\"PAID\"}", response.getBody());
    }

    @Test
    void createRefundForwardsRequestBody() {
        RefundOpsController controller = new RefundOpsController(new StubMushexClient());

        ResponseEntity<String> response = controller.createRefund("PI-1", "{\"amount\":25.0,\"reason\":\"Duplicate charge\"}", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"refundId\":\"RF-1\",\"status\":\"PENDING\"}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        // Pass all nulls — the compact constructor defaults every field to localhost URLs
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubMushexClient extends MushexServiceClient {
        private StubMushexClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> getPaymentIntent(String intentId) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"intentId\":\"" + intentId + "\",\"status\":\"PAID\"}");
        }

        @Override
        public ResponseEntity<String> createRefund(String intentId, String requestBody) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"refundId\":\"RF-1\",\"status\":\"PENDING\"}");
        }
    }
}
