package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.tshepo.contracts.v1.TrustChallengeDecision;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/**
 * The BFF used to answer every policy question with a {@code boolean}, and returned {@code false}
 * from its transport catch-all as well. Three different situations therefore reached the user as
 * one: "you may not", "you are not signed in strongly enough", and "the policy service is down".
 *
 * <p>These tests hold the separation open. They assert the DECISION, not the access outcome —
 * every case below still refuses the action.</p>
 */
class TrustDecisionResultTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private TshepoAuthzServiceClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        ServiceClientConfig.ServiceEndpoints endpoints = org.mockito.Mockito.mock(
                ServiceClientConfig.ServiceEndpoints.class);
        org.mockito.Mockito.when(endpoints.tshepoAuthzBaseUrl()).thenReturn("http://tshepo-authz:8081");
        client = new TshepoAuthzServiceClient(restTemplate, endpoints);
    }

    private void respond(String body) {
        server.expect(requestTo(containsString("/v1/authorize")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("an unreachable PDP is TEMPORARILY_UNAVAILABLE, never a denial")
    void outageIsNotDenial() {
        server.expect(requestTo(containsString("/v1/authorize"))).andRespond(withServerError());

        TrustDecisionResult r = client.authorize("POST", "/internal/v1/x");

        assertThat(r.allowed()).as("must still fail closed").isFalse();
        assertThat(r.decision())
                .as("telling a user they lack permissions when the PDP is down sends them to ask "
                        + "for access they already have")
                .isEqualTo(TrustChallengeDecision.TEMPORARILY_UNAVAILABLE);
        assertThat(r.decision()).isNotEqualTo(TrustChallengeDecision.DENY);
        assertThat(r.transportFailure()).isTrue();
        assertThat(r.actionable()).as("there is no user action that fixes an outage").isFalse();
    }

    @Test
    @DisplayName("a genuine DENY is a DENY — the unavailable path must not swallow real refusals")
    void denyIsStillDeny() {
        respond("{\"verdict\":\"DENY\",\"errorCode\":\"NO_MATCHING_RULE\"}");

        TrustDecisionResult r = client.authorize("POST", "/internal/v1/x");

        assertThat(r.decision()).isEqualTo(TrustChallengeDecision.DENY);
        assertThat(r.transportFailure())
                .as("a wrapper that reported every outcome as an outage would be just as blind")
                .isFalse();
        assertThat(r.outcome().reasonCode()).isEqualTo("NO_MATCHING_RULE");
        assertThat(r.actionable()).isFalse();
    }

    @Test
    @DisplayName("CONSENT_REQUIRED survives the legacy wire as an ACTIONABLE outcome")
    void consentRequiredIsActionable() {
        // The PDP can only carry this as a DENY carrying an error code; the adapter promotes it.
        respond("{\"verdict\":\"DENY\",\"errorCode\":\"CONSENT_REQUIRED\"}");

        TrustDecisionResult r = client.authorize("GET", "/internal/v1/patients/1");

        assertThat(r.allowed()).isFalse();
        assertThat(r.decision()).isEqualTo(TrustChallengeDecision.CONSENT_REQUIRED);
        assertThat(r.actionable())
                .as("a person who has never been asked has a next step; a dead end is the wrong answer")
                .isTrue();
        assertThat(r.outcome().requiredAction()).isEqualTo("OBTAIN_CONSENT");
    }

    @Test
    @DisplayName("an unrecognised deny code stays a DENY — promotion is an allowlist")
    void unknownDenyCodeIsNotPromoted() {
        respond("{\"verdict\":\"DENY\",\"errorCode\":\"SOME_FUTURE_REFUSAL\"}");

        TrustDecisionResult r = client.authorize("POST", "/internal/v1/x");

        assertThat(r.decision())
                .as("a new refusal reason must never silently gain a call to action")
                .isEqualTo(TrustChallengeDecision.DENY);
        assertThat(r.actionable()).isFalse();
    }

    @Test
    @DisplayName("step-up keeps its permitted methods instead of collapsing to false")
    void stepUpKeepsMethods() {
        respond("{\"verdict\":\"STEP_UP_REQUIRED\",\"stepUpMethods\":[\"otp\",\"webauthn\"]}");

        TrustDecisionResult r = client.authorize("POST", "/internal/v1/x");

        assertThat(r.decision()).isEqualTo(TrustChallengeDecision.STEP_UP_REQUIRED);
        assertThat(r.outcome().allowedAuthenticationMethods()).containsExactly("otp", "webauthn");
        assertThat(r.actionable()).isTrue();
    }

    @Test
    @DisplayName("a 403 carrying a body is the PDP answering, not a transport failure")
    void forbiddenWithBodyIsAnAnswer() {
        server.expect(requestTo(containsString("/v1/authorize")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"verdict\":\"DENY\",\"errorCode\":\"CONSENT_REQUIRED\"}"));

        TrustDecisionResult r = client.authorize("GET", "/internal/v1/patients/1");

        assertThat(r.transportFailure()).isFalse();
        assertThat(r.decision()).isEqualTo(TrustChallengeDecision.CONSENT_REQUIRED);
    }

    @Test
    @DisplayName("a 2xx with no verdict is a producer fault, not a refusal")
    void verdictlessBodyIsNotDenial() {
        respond("{\"somethingElse\":true}");

        TrustDecisionResult r = client.authorize("POST", "/internal/v1/x");

        assertThat(r.allowed()).isFalse();
        assertThat(r.decision()).isEqualTo(TrustChallengeDecision.TEMPORARILY_UNAVAILABLE);
    }

    @Test
    @DisplayName("ALLOW still allows — a client that refuses everything would pass every test above")
    void allowStillAllows() {
        respond("{\"verdict\":\"ALLOW\"}");

        TrustDecisionResult r = client.authorize("GET", "/internal/v1/x");

        assertThat(r.allowed())
                .as("without this, all the separation above could be achieved by denying everything")
                .isTrue();
        assertThat(r.decision()).isEqualTo(TrustChallengeDecision.ALLOW);
    }

    @Test
    @DisplayName("the boolean wrapper is the only place a decision is still flattened")
    void booleanCollapseIsContained() throws Exception {
        Path src = Path.of("src/main/java/zw/gov/mohcc/impilo/experience/client/"
                + "TshepoAuthzServiceClient.java");
        assertThat(Files.isRegularFile(src))
                .as("client source not found — this assertion would pass vacuously").isTrue();
        String body = Files.readString(src);

        // The HTTP call must live in exactly one place, and it must be the typed one. If a future
        // change re-adds a boolean-returning caller of /v1/authorize, this fails.
        assertThat(body.split("baseUrl \\+ \"/v1/authorize\"", -1).length - 1)
                .as("more than one authorize call site means the typed path can be bypassed")
                .isEqualTo(1);
        int typedStart = body.indexOf("public TrustDecisionResult authorize(");
        assertThat(typedStart).as("the typed method is gone").isGreaterThan(-1);
        assertThat(body.indexOf("baseUrl + \"/v1/authorize\"")).isGreaterThan(typedStart);
    }
}
