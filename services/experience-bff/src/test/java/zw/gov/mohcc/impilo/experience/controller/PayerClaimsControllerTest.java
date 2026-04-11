package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MushexServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayerClaimsControllerTest {

    @Test
    void listClaimsForwardsPagedPayload() {
        PayerClaimsController controller = new PayerClaimsController(new StubMushexClient());

        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("status", "SUBMITTED");

        ResponseEntity<String> response = controller.listClaims(query, "req-0", "corr-0");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"items\":[{\"claimId\":\"CLAIM-1\"}],\"page\":0}", response.getBody());
    }

    @Test
    void getClaimForwardsClaimPayload() {
        PayerClaimsController controller = new PayerClaimsController(new StubMushexClient());

        ResponseEntity<String> response = controller.getClaim("CLAIM-1", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("req-1", response.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("{\"claimId\":\"CLAIM-1\",\"status\":\"DRAFT\"}", response.getBody());
    }

    @Test
    void disputeClaimForwardsDisputePayload() {
        PayerClaimsController controller = new PayerClaimsController(new StubMushexClient());

        ResponseEntity<String> response = controller.disputeClaim("CLAIM-1", "{\"reason\":\"Incorrect amount\"}", "req-2", "corr-2");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("corr-2", response.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("{\"claimId\":\"CLAIM-1\",\"status\":\"DISPUTED\"}", response.getBody());
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
        public ResponseEntity<String> listClaims(MultiValueMap<String, String> queryParams) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"items\":[{\"claimId\":\"CLAIM-1\"}],\"page\":0}");
        }

        @Override
        public ResponseEntity<String> getClaim(String claimId) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"claimId\":\"" + claimId + "\",\"status\":\"DRAFT\"}");
        }

        @Override
        public ResponseEntity<String> disputeClaim(String claimId, String requestBody) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"claimId\":\"" + claimId + "\",\"status\":\"DISPUTED\"}");
        }
    }
}
