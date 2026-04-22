package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayerOpsControllerTest {

    @Test
    void listOpsReviewsForwardsPayloadWithMetadata() {
        PayerOpsController controller = new PayerOpsController(new StubMushexClient());
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("status", "PENDING");

        ResponseEntity<String> response = controller.listOpsReviews(query, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"items\":[{\"id\":\"OPS-1\",\"status\":\"PENDING\"}]}", response.getBody());
    }

    @Test
    void issueRemittanceSlipForwardsAction() {
        PayerOpsController controller = new PayerOpsController(new StubMushexClient());

        ResponseEntity<String> response = controller.issueRemittanceSlip("PI-1", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"id\":\"REM-1\",\"status\":\"ACTIVE\"}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
    }

    private static final class StubMushexClient extends MushexServiceClient {
        private StubMushexClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> listOpsReviews(MultiValueMap<String, String> queryParams) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"items\":[{\"id\":\"OPS-1\",\"status\":\"PENDING\"}]}");
        }

        @Override
        public ResponseEntity<String> issueRemittanceSlip(String intentId) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"id\":\"REM-1\",\"status\":\"ACTIVE\"}");
        }
    }
}
