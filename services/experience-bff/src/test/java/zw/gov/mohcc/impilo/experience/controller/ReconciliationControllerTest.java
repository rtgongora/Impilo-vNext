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

class ReconciliationControllerTest {

    @Test
    void listUnmatchedForwardsPayloadWithMetadata() {
        ReconciliationController controller = new ReconciliationController(new StubMushexClient());
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("page", "0");

        ResponseEntity<String> response = controller.getUnmatched(query, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("{\"items\":[{\"id\":\"REC-1\",\"status\":\"UNMATCHED\"}]}", response.getBody());
    }

    @Test
    void matchEntryForwardsRequestBody() {
        ReconciliationController controller = new ReconciliationController(new StubMushexClient());

        ResponseEntity<String> response = controller.matchEntry("REC-1", "{\"intentId\":\"PI-1\"}", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"id\":\"REC-1\",\"status\":\"MATCHED\"}", response.getBody());
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        // Pass all nulls — the compact constructor defaults every field to localhost URLs
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
    }
}
