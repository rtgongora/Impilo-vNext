package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommerceFlowControllerTest {

    @Test
    void createOrderForwardsUpstreamPayloadWithMetadata() {
        CommerceFlowController controller = new CommerceFlowController(new StubFlowClient());

        ResponseEntity<String> response = controller.createOrder(
                "{\"orderType\":\"FACILITY_PROCUREMENT\",\"facilityId\":\"f100\",\"lines\":[]}",
                "req-1",
                "corr-1"
        );

        assertEquals(201, response.getStatusCode().value());
        assertEquals("application/json", response.getHeaders().getContentType().toString());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"data\":{\"orderId\":\"ORDER-001\"}}", response.getBody());
    }

    @Test
    void payOrderForwardsPaymentIntentPayload() {
        CommerceFlowController controller = new CommerceFlowController(new StubFlowClient());

        ResponseEntity<String> response = controller.payOrder("ORDER-001", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-2", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-2", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"data\":{\"mushexPaymentIntentId\":\"PI-001\"}}", response.getBody());
    }

    @Test
    void issuePickupTokenForwardsPickupPayload() {
        CommerceFlowController controller = new CommerceFlowController(new StubFlowClient());

        ResponseEntity<String> response = controller.issuePickupToken("ORDER-001", "req-3", "corr-3");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-3", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("{\"data\":{\"pickupToken\":\"PICKUP-001\"}}", response.getBody());
    }

    @Test
    void claimPickupForwardsClaimPayload() {
        CommerceFlowController controller = new CommerceFlowController(new StubFlowClient());

        ResponseEntity<String> response = controller.claimPickup(
                "{\"pickupToken\":\"PICKUP-001\",\"claimerNationalId\":\"63-123456A63\"}",
                "req-4",
                "corr-4"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("corr-4", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"data\":{\"status\":\"CLAIMED\"}}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null
        );
    }

    private static final class StubFlowClient extends MsikaFlowServiceClient {
        private StubFlowClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> createOrder(String requestBody) {
            return ResponseEntity.status(201)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":{\"orderId\":\"ORDER-001\"}}");
        }

        @Override
        public ResponseEntity<String> payOrder(String orderId) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":{\"mushexPaymentIntentId\":\"PI-001\"}}");
        }

        @Override
        public ResponseEntity<String> issuePickupToken(String orderId) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":{\"pickupToken\":\"PICKUP-001\"}}");
        }

        @Override
        public ResponseEntity<String> claimPickup(String requestBody) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":{\"status\":\"CLAIMED\"}}");
        }
    }
}
