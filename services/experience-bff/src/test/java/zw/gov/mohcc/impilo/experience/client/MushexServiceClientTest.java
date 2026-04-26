package zw.gov.mohcc.impilo.experience.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MushexServiceClientTest {

    @Test
    void runSettlementPostsToCanonicalMushexRoute() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MushexServiceClient client = new MushexServiceClient(restTemplate, endpoints());

        server.expect(requestTo("http://mushex/mushex/v1/settlements/run"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"periodStart\":\"2026-04-01\",\"periodEnd\":\"2026-04-30\"}"))
                .andRespond(withSuccess("{\"settlementId\":\"SET-001\"}", MediaType.APPLICATION_JSON));

        assertEquals(
                "{\"settlementId\":\"SET-001\"}",
                client.runSettlement("{\"periodStart\":\"2026-04-01\",\"periodEnd\":\"2026-04-30\"}").getBody()
        );
        server.verify();
    }

    @Test
    void getUnmatchedUsesCanonicalReconciliationRoute() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MushexServiceClient client = new MushexServiceClient(restTemplate, endpoints());
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("page", "0");
        query.add("size", "20");

        server.expect(requestTo("http://mushex/mushex/v1/recon/unmatched?page=0&size=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        assertEquals("{\"items\":[]}", client.getUnmatched(query).getBody());
        server.verify();
    }

    @Test
    void getLedgerUsesCanonicalLedgerRoute() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MushexServiceClient client = new MushexServiceClient(restTemplate, endpoints());
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("intentId", "PI-001");

        server.expect(requestTo("http://mushex/mushex/v1/ledger?intentId=PI-001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"entries\":[]}", MediaType.APPLICATION_JSON));

        assertEquals("{\"entries\":[]}", client.getLedger(query).getBody());
        server.verify();
    }

    @Test
    void submitClaimUsesCanonicalClaimSubmissionRoute() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        MushexServiceClient client = new MushexServiceClient(restTemplate, endpoints());

        server.expect(requestTo("http://mushex/mushex/v1/claims/CLAIM-1/submit"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"claimId\":\"CLAIM-1\",\"status\":\"SUBMITTED\"}", MediaType.APPLICATION_JSON));

        assertEquals(
                "{\"claimId\":\"CLAIM-1\",\"status\":\"SUBMITTED\"}",
                client.submitClaim("CLAIM-1").getBody()
        );
        server.verify();
    }

    private static ServiceClientConfig.ServiceEndpoints endpoints() {
        return new ServiceClientConfig.ServiceEndpoints(
                "http://pct", "http://oros", "http://pharmacy", "http://butano",
                "http://msika", "http://msika-flow", "http://mushex", "http://vito",
                "http://tuso", "http://varapi", "http://documents", "http://costa",
                "http://coverage", "http://surveillance", "http://campaigns", "http://indawo",
                "http://governance", "http://landela", "http://notifications",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null
        );
    }
}
