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

        ResponseEntity<String> response = controller.releasePayouts("SET-001", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"settlementId\":\"SET-001\",\"status\":\"RELEASED\"}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        // Pass all nulls — the compact constructor defaults every field to localhost URLs
        return new ServiceClientConfig.ServiceEndpoints(
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
    }

    private static final class StubMushexClient extends MushexServiceClient {
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
        public ResponseEntity<String> releasePayouts(String settlementId) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"settlementId\":\"" + settlementId + "\",\"status\":\"RELEASED\"}");
        }
    }
}
