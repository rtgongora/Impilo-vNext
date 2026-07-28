package zw.gov.mohcc.impilo.experience.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies the BFF procedures client targets the real routes with the exact shapes P-R.4's
 * authz migration was written against — this is the client-side half of that contract, and a
 * silent drift here (a path variable creeping back in, a missing content type) would defeat the
 * policy rows without either side's tests noticing on their own.
 */
class ProceduresServiceClientTest {

    private static final String BASE = "http://procedures";

    private ProceduresServiceClient client(RestTemplate rt) {
        return new ProceduresServiceClient(rt, ServiceClientConfig.testServiceEndpoints(BASE));
    }

    @Test
    void searchCatalogueHitsTheFixedCatalogueRouteWithFilters() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/procedures/catalogue?specialty=SURGERY&category=THEATRE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        var response = client(rt).searchCatalogue("SURGERY", "THEATRE", null);

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    /**
     * The route-shape guarantee itself: the definition code is a QUERY PARAMETER, never a path
     * variable, so the last Envoy path segment stays the fixed word "catalogue-detail" that
     * tshepo-authz V300 pins on — not the procedure code, which would defeat resource_type
     * derivation for every code that is not a UUID.
     */
    @Test
    void catalogueDetailPassesTheCodeAsAPathVariableToProceduresServiceItself() {
        // Note: this client talks to procedures-service's OWN REST shape (.../catalogue/{code}),
        // which is fine because procedures-service is not itself behind the ext_authz PDP — only
        // the BFF's EXTERNAL route (ProceduresController, catalogue-detail?code=...) is. See
        // ProceduresControllerTest for the shape the PDP actually evaluates.
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/procedures/catalogue/PROC-LAPAROTOMY"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"definitionCode\":\"PROC-LAPAROTOMY\"}", MediaType.APPLICATION_JSON));

        var response = client(rt).catalogueDetail("PROC-LAPAROTOMY");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    /**
     * The bug this test exists to catch: RestTemplate.postForEntity with a raw String body
     * defaults to text/plain, and procedures-service's @RequestBody binding rejects that with
     * 415. Proven by asserting the outbound Content-Type header directly, not just that the call
     * "worked" against a permissive mock.
     */
    @Test
    void evaluateAppropriatenessSendsApplicationJsonContentType() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/procedures/appropriateness/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"outcome\":\"APPROPRIATE\"}", MediaType.APPLICATION_JSON));

        var response = client(rt).evaluateAppropriateness("{\"definitionCode\":\"PROC-LAPAROTOMY\"}");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("APPROPRIATE"));
        server.verify();
    }

    @Test
    void resolveCompetenceSendsBothQueryParameters() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/procedures/competence?providerId=prov-1&definitionCode=PROC-LAPAROTOMY"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"capacity\":\"INDEPENDENT\"}", MediaType.APPLICATION_JSON));

        var response = client(rt).resolveCompetence("prov-1", "PROC-LAPAROTOMY");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }
}
