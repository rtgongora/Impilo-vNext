package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementControllerTest {

    @Test
    void runSettlementForwardsPayloadWithMetadata() {
        SettlementController controller = new SettlementController(new StubMushexClient());

        ResponseEntity<String> response = controller.runSettlement(
                "{\"periodStart\":\"2026-04-01\",\"periodEnd\":\"2026-04-30\"}",
                "req-1",
                "corr-1"
        );

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/json", response.getHeaders().getContentType().toString());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-1", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"settlementId\":\"SET-001\",\"status\":\"COMPUTING\"}", response.getBody());
    }

    @Test
    void releasePayoutsForwardsUpstreamSettlementState() {
        SettlementController controller = new SettlementController(new StubMushexClient());

        ResponseEntity<String> response = controller.releasePayouts("SET-001", null, "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"settlementId\":\"SET-001\",\"status\":\"RELEASED\"}", response.getBody());
    }

    @Test
    void releasePayoutsForwardsOptionalBiometricStepUpBody() {
        StubMushexClient stub = new StubMushexClient();
        SettlementController controller = new SettlementController(stub);

        String body = "{\"biometricSubjectRef\":\"officer-3\",\"biometricModality\":\"FINGERPRINT\",\"biometricProbeBase64\":\"TPL==\"}";
        ResponseEntity<String> response = controller.releasePayouts("SET-001", body, "req-2b", "corr-2b");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(body, stub.lastReleaseBody);
        assertEquals("req-2b", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
    }

    @Test
    void listSettlementsForwardsIntentFilters() {
        SettlementController controller = new SettlementController(new StubMushexClient());

        ResponseEntity<String> response = controller.listSettlements("PI-1", java.util.List.of("PI-1", "PI-2"), "req-3", "corr-3");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"data\":[{\"settlementId\":\"SET-001\"}]}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        // Pass all nulls — the compact constructor defaults every field to localhost URLs
        return ServiceClientConfig.testServiceEndpoints();
    }

    private static final class StubMushexClient extends MushexServiceClient {
        private String lastReleaseBody = "UNSET";

        private StubMushexClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> runSettlement(String requestBody) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"settlementId\":\"SET-001\",\"status\":\"COMPUTING\"}");
        }

        @Override
        public ResponseEntity<String> releasePayouts(String settlementId, String requestBody) {
            this.lastReleaseBody = requestBody;
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"settlementId\":\"" + settlementId + "\",\"status\":\"RELEASED\"}");
        }

        @Override
        public ResponseEntity<String> listSettlements(String intentId, java.util.List<String> intentIds) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"data\":[{\"settlementId\":\"SET-001\"}]}");
        }
    }
}
