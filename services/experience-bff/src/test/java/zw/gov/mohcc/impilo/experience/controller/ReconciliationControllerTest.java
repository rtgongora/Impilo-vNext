package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconciliationControllerTest {

    @Test
    void listUnmatchedForwardsPayloadWithMetadata() {
        ReconciliationController controller = new ReconciliationController(new StubMushexClient(), new StubCostaClient());
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("page", "0");

        ResponseEntity<String> response = controller.getUnmatched(query, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("{\"items\":[{\"id\":\"REC-1\",\"status\":\"UNMATCHED\"}]}", response.getBody());
    }

    @Test
    void matchEntryForwardsRequestBody() {
        ReconciliationController controller = new ReconciliationController(new StubMushexClient(), new StubCostaClient());

        ResponseEntity<String> response = controller.matchEntry("REC-1", "{\"intentId\":\"PI-1\"}", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"id\":\"REC-1\",\"status\":\"MATCHED\"}", response.getBody());
    }

    @Test
    void tripleMatchBuildsJoinedResponse() {
        ReconciliationController controller = new ReconciliationController(new StubMushexClient(), new StubCostaClient());

        ResponseEntity<String> response = controller.tripleMatch("ENC-1", "req-3", "corr-3");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-3", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-3", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        String body = response.getBody();
        assert body != null;
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"invoice_id\":\"INV-1\""));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"mushex_intent_id\":\"PI-1\""));
    }

    @Test
    void tripleMatchFailClosesWhenUpstreamUnavailable() {
        ReconciliationController controller = new ReconciliationController(new StubMushexClient(), new FailingCostaClient());

        ResponseEntity<String> response = controller.tripleMatch("ENC-2", "req-4", "corr-4");

        assertEquals(502, response.getStatusCode().value());
        String body = response.getBody();
        org.junit.jupiter.api.Assertions.assertNotNull(body);
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("\"code\":\"RECONCILIATION_UNAVAILABLE\""));
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
        public ResponseEntity<String> getUnmatched(MultiValueMap<String, String> queryParams) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"items\":[{\"id\":\"REC-1\",\"status\":\"UNMATCHED\"}]}");
        }

        @Override
        public ResponseEntity<String> matchEntry(String reconId, String requestBody) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"id\":\"" + reconId + "\",\"status\":\"MATCHED\"}");
        }

        @Override
        public ResponseEntity<String> listPaymentIntentsBySource(String sourceType, String sourceId, java.util.List<String> sourceIds) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":[{\"intentId\":\"PI-1\",\"sourceType\":\"COSTA_BILL\",\"sourceId\":\"BILL-1\",\"status\":\"PENDING\"}]}");
        }

        @Override
        public ResponseEntity<String> listSettlements(String intentId, java.util.List<String> intentIds) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":[{\"settlementId\":\"SET-1\"}]}");
        }
    }

    private static final class StubCostaClient extends CostaServiceClient {
        private StubCostaClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> financeLifecycleGet(String relativePath) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":[{\"invoice_id\":\"INV-1\",\"bill_id\":\"BILL-1\"}]}");
        }
    }

    private static final class FailingCostaClient extends CostaServiceClient {
        private FailingCostaClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> financeLifecycleGet(String relativePath) {
            throw new RuntimeException("costa unavailable");
        }
    }
}
