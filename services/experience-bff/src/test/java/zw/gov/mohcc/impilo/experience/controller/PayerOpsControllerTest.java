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
    void listAdaptersForwardsMusheXRegistry() {
        PayerOpsController controller = new PayerOpsController(new StubMushexClient());
        ResponseEntity<String> response = controller.listAdapters("req-adapters", "corr-adapters");
        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"items\":[{\"id\":\"ADP-SANDBOX\",\"rail\":\"SANDBOX\"}]}", response.getBody());
    }

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

    @Test
    void initiatePaymentAttemptForwardsBodyAndIdempotencyKey() {
        AttemptCapturingClient capturing = new AttemptCapturingClient();
        PayerOpsController controller = new PayerOpsController(capturing);

        ResponseEntity<String> response = controller.initiatePaymentAttempt(
                "PI-9", "{\"reason\":\"manual-retry\"}", "idem-att-7", "req-3", "corr-3");

        assertEquals(201, response.getStatusCode().value());
        assertEquals("corr-3", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("PI-9", capturing.intentId);
        assertEquals("{\"reason\":\"manual-retry\"}", capturing.body);
        assertEquals("idem-att-7", capturing.idempotencyKey);
    }

    @Test
    void initiatePaymentAttemptToleratesMissingBodyAndHeader() {
        AttemptCapturingClient capturing = new AttemptCapturingClient();
        PayerOpsController controller = new PayerOpsController(capturing);

        ResponseEntity<String> response = controller.initiatePaymentAttempt(
                "PI-10", null, null, "req-4", "corr-4");

        assertEquals(201, response.getStatusCode().value());
        assertEquals("PI-10", capturing.intentId);
        assertEquals(null, capturing.idempotencyKey);
    }

    @Test
    void listPaymentAttemptsForwards() {
        AttemptListCapturingClient capturing = new AttemptListCapturingClient();
        PayerOpsController controller = new PayerOpsController(capturing);

        ResponseEntity<String> response = controller.listPaymentAttempts(
                "PI-50", "req-5", "corr-5");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("PI-50", capturing.intentId);
        assertEquals("corr-5", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"data\":[]}", response.getBody());
    }

    @Test
    void reselectRailForwardsBody() {
        ReselectCapturingClient capturing = new ReselectCapturingClient();
        PayerOpsController controller = new PayerOpsController(capturing);

        ResponseEntity<String> response = controller.reselectRail(
                "PI-60", "{\"reason\":\"provider-outage\"}", "req-6", "corr-6");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("PI-60", capturing.intentId);
        assertEquals("{\"reason\":\"provider-outage\"}", capturing.body);
    }

    @Test
    void reselectRailTolersMissingBody() {
        ReselectCapturingClient capturing = new ReselectCapturingClient();
        PayerOpsController controller = new PayerOpsController(capturing);

        ResponseEntity<String> response = controller.reselectRail(
                "PI-61", null, "req-7", "corr-7");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("PI-61", capturing.intentId);
        assertEquals(null, capturing.body);
    }

    @Test
    void claimRemittanceForwardsBody() {
        ClaimCapturingClient capturing = new ClaimCapturingClient();
        PayerOpsController controller = new PayerOpsController(capturing);

        ResponseEntity<String> response = controller.claimRemittance(
                "{\"slipId\":\"REM-9\",\"tenantId\":\"tenant-1\"}", "req-9", "corr-9");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"slipId\":\"REM-9\",\"tenantId\":\"tenant-1\"}", capturing.body);
        assertEquals("{\"status\":\"CLAIMED\"}", response.getBody());
    }

    @Test
    void listPaymentIntentsBySourceForwardsQuery() {
        SourceIntentCapturingClient capturing = new SourceIntentCapturingClient();
        PayerOpsController controller = new PayerOpsController(capturing);

        ResponseEntity<String> response = controller.listPaymentIntentsBySource(
                "COSTA_BILL", null, java.util.List.of("BILL-1", "BILL-2"), "req-8", "corr-8");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("COSTA_BILL", capturing.sourceType);
        assertEquals(2, capturing.sourceIds.size());
        assertEquals("{\"data\":[]}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return ServiceClientConfig.testServiceEndpoints();
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

        @Override
        public ResponseEntity<String> listAdapters() {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"items\":[{\"id\":\"ADP-SANDBOX\",\"rail\":\"SANDBOX\"}]}");
        }
    }

    private static final class AttemptCapturingClient extends MushexServiceClient {
        String intentId;
        String body;
        String idempotencyKey;

        private AttemptCapturingClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> initiatePaymentAttempt(String intentId, String requestBody, String idempotencyKey) {
            this.intentId = intentId;
            this.body = requestBody;
            this.idempotencyKey = idempotencyKey;
            return ResponseEntity.status(201).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"success\":true,\"data\":{\"id\":\"ATT-1\"}}");
        }
    }

    private static final class AttemptListCapturingClient extends MushexServiceClient {
        String intentId;

        private AttemptListCapturingClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> listPaymentAttempts(String intentId) {
            this.intentId = intentId;
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":[]}");
        }
    }

    private static final class ReselectCapturingClient extends MushexServiceClient {
        String intentId;
        String body;

        private ReselectCapturingClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> reselectRail(String intentId, String requestBody) {
            this.intentId = intentId;
            this.body = requestBody;
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"success\":true,\"data\":{\"intentId\":\"" + intentId + "\"}}");
        }
    }

    private static final class ClaimCapturingClient extends MushexServiceClient {
        String body;

        private ClaimCapturingClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> claimRemittance(String requestBody) {
            this.body = requestBody;
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{\"status\":\"CLAIMED\"}");
        }
    }

    private static final class SourceIntentCapturingClient extends MushexServiceClient {
        String sourceType;
        java.util.List<String> sourceIds = java.util.List.of();

        private SourceIntentCapturingClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> listPaymentIntentsBySource(String sourceType, String sourceId, java.util.List<String> sourceIds) {
            this.sourceType = sourceType;
            this.sourceIds = sourceIds == null ? java.util.List.of() : sourceIds;
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{\"data\":[]}");
        }
    }
}
