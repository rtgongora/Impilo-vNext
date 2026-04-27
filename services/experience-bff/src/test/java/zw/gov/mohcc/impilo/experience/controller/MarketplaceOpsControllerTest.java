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

class MarketplaceOpsControllerTest {

    @Test
    void listVendorsForwardsVendorDirectory() {
        MarketplaceOpsController controller = new MarketplaceOpsController(new StubFlowClient());
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("status", "ACTIVE");

        ResponseEntity<String> response = controller.listVendors(queryParams, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("{\"items\":[{\"vendorId\":\"vendor-1\",\"status\":\"ACTIVE\"}]}", response.getBody());
    }

    @Test
    void approveReviewForwardsDecision() {
        MarketplaceOpsController controller = new MarketplaceOpsController(new StubFlowClient());

        ResponseEntity<String> response = controller.approveReview("review-1", "{\"notes\":\"ok\"}", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("corr-2", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"data\":{\"reviewId\":\"review-1\",\"decision\":\"APPROVED\"}}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubFlowClient extends MsikaFlowServiceClient {
        private StubFlowClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> listVendors(MultiValueMap<String, String> queryParams) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"items\":[{\"vendorId\":\"vendor-1\",\"status\":\"" + queryParams.getFirst("status") + "\"}]}");
        }

        @Override
        public ResponseEntity<String> approveOpsReview(String reviewId, String requestBody) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":{\"reviewId\":\"" + reviewId + "\",\"decision\":\"APPROVED\"}}");
        }
    }
}
