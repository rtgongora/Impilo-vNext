package zw.gov.mohcc.impilo.experience.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies the BFF surgery client targets the real surgery-service routes with the exact
 * shapes V302's authz rows are written against — the client-side half of that contract, same
 * discipline as {@link ProceduresServiceClientTest}. The load-bearing assertions are the URL
 * shapes (episode id as a UUID path segment, subjectCpid as a query parameter) and the
 * explicit application/json content type on every write (a raw-String POST/PUT otherwise
 * defaults to text/plain and surgery-service's @RequestBody binding rejects it with 415).
 */
class SurgeryServiceClientTest {

    private static final String BASE = "http://surgery";
    private static final UUID EPISODE = UUID.fromString("3f9a1c2e-4b5d-4a6f-9c7e-8d0f1a2b3c4d");

    private SurgeryServiceClient client(RestTemplate rt) {
        return new SurgeryServiceClient(rt, ServiceClientConfig.testServiceEndpoints(BASE));
    }

    @Test
    void openEpisodeSendsApplicationJsonToTheCollectionRoute() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"id\":\"" + EPISODE + "\"}", MediaType.APPLICATION_JSON));

        var response = client(rt).openEpisode("{\"subjectCpid\":\"CPID-1\"}");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains(EPISODE.toString()));
        server.verify();
    }

    @Test
    void episodesForSubjectSendsTheCpidAsAQueryParameter() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes?subjectCpid=CPID-1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var response = client(rt).episodesForSubject("CPID-1");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    @Test
    void episodeDetailUsesTheUuidAsAPathSegment() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"" + EPISODE + "\"}", MediaType.APPLICATION_JSON));

        var response = client(rt).episode(EPISODE);

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    @Test
    void linkProcedureEpisodePostsJsonToItsOwnSegment() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE + "/link-procedure-episode"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"id\":\"" + EPISODE + "\"}", MediaType.APPLICATION_JSON));

        var response = client(rt).linkProcedureEpisode(EPISODE, "{\"procedureEpisodeRef\":\"x\"}");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    @Test
    void transitionPostsJsonToItsOwnSegment() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE + "/transition"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"status\":\"LISTED_FOR_SURGERY\"}", MediaType.APPLICATION_JSON));

        var response = client(rt).transition(EPISODE, "{\"status\":\"LISTED_FOR_SURGERY\"}");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    /** The S2 write is a PUT — supported by the estate's client stack, unlike PATCH. */
    @Test
    void recordAssessmentPutsJsonToTheAssessmentRoute() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE + "/assessment"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"presentingProblem\":\"x\"}", MediaType.APPLICATION_JSON));

        var response = client(rt).recordAssessment(EPISODE, "{\"presentingProblem\":\"x\"}");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    @Test
    void assessmentReadHitsTheSameFixedSegment() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE + "/assessment"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        var response = client(rt).assessment(EPISODE);

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    @Test
    void recordDecisionPutsJsonToTheDecisionRoute() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE + "/decision"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"finalDecision\":\"PROCEED\"}", MediaType.APPLICATION_JSON));

        var response = client(rt).recordDecision(EPISODE, "{\"finalDecision\":\"PROCEED\"}");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    @Test
    void decisionReadHitsTheSameFixedSegment() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE + "/decision"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        var response = client(rt).decision(EPISODE);

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    // ── Completion wave: reoperation (V010) and shared-specialty care (V011) ──

    @Test
    void reopenPostsJsonToItsOwnSegment() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE + "/reopen"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"status\":\"REOPENED\"}", MediaType.APPLICATION_JSON));

        var response = client(rt).reopen(EPISODE, "{\"reason\":\"post-operative haemorrhage\"}");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    @Test
    void specialtiesReadAndAddShareOneFixedSegment() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE + "/specialties"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE + "/specialties"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"specialty\":\"VASCULAR\"}", MediaType.APPLICATION_JSON));

        assertEquals(200, client(rt).specialties(EPISODE).getStatusCode().value());
        assertEquals(200, client(rt).addSpecialty(EPISODE, "{\"specialty\":\"VASCULAR\"}")
                .getStatusCode().value());
        server.verify();
    }

    @Test
    void leadHandoverPostsJsonToItsOwnSegment() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE + "/specialties/lead"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var response = client(rt).transferLead(EPISODE, "{\"specialty\":\"VASCULAR\"}");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    /**
     * The load-bearing one. If the specialty ever moves back into the path, ext_authz derives
     * the specialty itself as the resource type and V303's rows can never match — the route
     * becomes permanently unreachable. This asserts the query-parameter shape, not just that
     * a DELETE is issued.
     */
    @Test
    void removeSpecialtySendsTheSpecialtyAsAQueryParameterNotAPathSegment() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/episodes/" + EPISODE
                        + "/specialties?specialty=VASCULAR"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var response = client(rt).removeSpecialty(EPISODE, "VASCULAR");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    // ── Specialty catalogue + analytics (V305) ──

    @Test
    void specialtyIndicationsSendsSpecialtyAsAQueryParameterOnTheCataloguePath() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/specialties/indications?specialty=COLORECTAL"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var response = client(rt).specialtyIndications("COLORECTAL");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    @Test
    void specialtyTemplatesSendsSpecialtyAsAQueryParameterOnTheCataloguePath() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/specialties/templates?specialty=COLORECTAL"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        var response = client(rt).specialtyTemplates("COLORECTAL");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    @Test
    void analyticsIndicatorsHitsTheSurgeryAnalyticsCatalogue() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/analytics/indicators"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"total\":0,\"indicators\":[]}", MediaType.APPLICATION_JSON));

        var response = client(rt).analyticsIndicators();

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }

    @Test
    void analyticsIndicatorSendsCodeAsAQueryParameter() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/internal/v1/surgery/analytics/indicator?code=SSI_RATE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"indicatorCode\":\"SSI_RATE\"}", MediaType.APPLICATION_JSON));

        var response = client(rt).analyticsIndicator("SSI_RATE");

        assertEquals(200, response.getStatusCode().value());
        server.verify();
    }
}
