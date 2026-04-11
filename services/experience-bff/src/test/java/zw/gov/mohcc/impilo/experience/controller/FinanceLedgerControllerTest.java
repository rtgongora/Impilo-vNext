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

class FinanceLedgerControllerTest {

    @Test
    void getLedgerForwardsIntentScopedLedgerPayload() {
        FinanceLedgerController controller = new FinanceLedgerController(new StubMushexClient());
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("intentId", "PI-001");

        ResponseEntity<String> response = controller.getLedger(queryParams, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("{\"entries\":[{\"intentId\":\"PI-001\"}]}", response.getBody());
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

    private static final class StubMushexClient extends MushexServiceClient {
        private StubMushexClient() {
            super(new RestTemplate(), endpoints());
        }

        @Override
        public ResponseEntity<String> getLedger(MultiValueMap<String, String> queryParams) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"entries\":[{\"intentId\":\"" + queryParams.getFirst("intentId") + "\"}]}");
        }
    }
}
