package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.ConsentDecision;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/**
 * Pins this client against the producer it actually calls.
 *
 * <p>The previous version POSTed a body to a GET-only endpoint, with three wrong parameter names,
 * one missing required parameter, and no envelope handling — five simultaneous mismatches. None of
 * it ever failed a test, because the client's own catch-all converts every error into
 * {@code deny("CONSENT_SERVICE_UNAVAILABLE")}: fail-closed hid a call that could not succeed.</p>
 *
 * <p>So the tests that matter here assert the REQUEST, not just the returned decision, and one of
 * them reads the producer's controller source directly — a test that only checked this side would
 * have passed against the broken version too.</p>
 */
class ConsentClientContractTest {

    private static final Path PRODUCER = Path.of(
            "../tshepo-consent-service/src/main/java/zw/gov/mohcc/impilo/tshepo/consent/api/"
                    + "ConsentEvaluationController.java");

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private ConsentClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        AuthzProperties props = new AuthzProperties();
        props.setConsentServiceUrl("http://tshepo-consent-service:8182");
        props.setConsentEvaluatePath("/v1/consent/evaluate");
        client = new ConsentClient(restTemplate, props);
    }

    private static String envelope(String data) {
        return "{\"data\":" + data + ",\"correlationId\":\"c-1\"}";
    }

    @Test
    @DisplayName("calls GET with every parameter the producer requires, and none it does not know")
    void requestMatchesProducerContract() {
        server.expect(requestTo(containsString("/v1/consent/evaluate")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("tenantId", TENANT.toString()))
                .andExpect(queryParam("actorId", "actor-1"))
                .andExpect(queryParam("subjectRef", "CPID-9"))
                .andExpect(queryParam("purpose", "TREATMENT"))
                .andExpect(queryParam("scope", ConsentClient.DEFAULT_SCOPE))
                .andRespond(withSuccess(envelope("{\"permitted\":true,\"consentId\":\"c-9\"}"),
                        MediaType.APPLICATION_JSON));

        ConsentDecision d = client.evaluateConsent(TENANT, "Patient", "CPID-9", "actor-1", "TREATMENT");

        assertThat(d.permitted()).isTrue();
        assertThat(d.consentId()).isEqualTo("c-9");
        server.verify();
    }

    @Test
    @DisplayName("the producer's required parameters are exactly what this client sends")
    void producerSignatureMatchesWhatWeSend() throws Exception {
        assertThat(Files.isRegularFile(PRODUCER))
                .as("producer controller not found at %s — this assertion would pass vacuously",
                        PRODUCER.toAbsolutePath())
                .isTrue();

        String src = Files.readString(PRODUCER);
        // Everything the producer declares as a required @RequestParam on the evaluate endpoint.
        int start = src.indexOf("@GetMapping(\"/evaluate\")");
        assertThat(start).as("no /evaluate mapping found — nothing to compare against").isGreaterThan(-1);
        String signature = src.substring(start, src.indexOf(')', src.indexOf('(', start + 30)) + 1);

        Set<String> required = new TreeSet<>();
        Matcher m = Pattern.compile("@RequestParam\\s+\\w+<?\\w*>?\\s+(\\w+)").matcher(signature);
        while (m.find()) {
            required.add(m.group(1));
        }
        assertThat(required).as("parsed no @RequestParams — the regex is wrong, test proves nothing")
                .isNotEmpty();

        // Capture what the client actually puts on the wire.
        server.expect(requestTo(containsString("/v1/consent/evaluate")))
                .andRespond(withSuccess(envelope("{\"permitted\":true}"), MediaType.APPLICATION_JSON));
        client.evaluateConsent(TENANT, "Patient", "CPID-9", "actor-1", "TREATMENT");
        server.verify();

        // Every producer-required param must be one this client sends. `resourceType` and
        // `resourceId` are deliberately absent: sending them was the original defect.
        assertThat(required)
                .as("the producer requires a parameter this client does not send")
                .containsExactlyInAnyOrder("tenantId", "actorId", "subjectRef", "purpose", "scope");
    }

    @Test
    @DisplayName("unwraps the ApiResponse envelope — a bare-decision reader sees permitted=false")
    void unwrapsEnvelope() {
        server.expect(requestTo(containsString("/v1/consent/evaluate")))
                .andRespond(withSuccess(
                        envelope("{\"permitted\":true,\"provision\":\"permit\","
                                + "\"allowedScopes\":[\"read\"],\"deniedScopes\":[]}"),
                        MediaType.APPLICATION_JSON));

        ConsentDecision d = client.evaluateConsent(TENANT, "Patient", "CPID-9", "actor-1", "TREATMENT");

        assertThat(d.permitted()).isTrue();
        assertThat(d.provision()).isEqualTo("permit");
        assertThat(d.allowedScopes()).containsExactly("read");
    }

    @Test
    @DisplayName("an envelope carrying an error is a malformed response, not a clean denial")
    void errorEnvelopeIsDistinguished() {
        server.expect(requestTo(containsString("/v1/consent/evaluate")))
                .andRespond(withSuccess("{\"error\":{\"code\":\"CONSENT_LOOKUP_FAILED\"}}",
                        MediaType.APPLICATION_JSON));

        ConsentDecision d = client.evaluateConsent(TENANT, "Patient", "CPID-9", "actor-1", "TREATMENT");

        assertThat(d.permitted()).isFalse();
        assertThat(d.reason())
                .as("a producer fault must not be reported as an ordinary 'no consent' outcome")
                .isEqualTo("CONSENT_RESPONSE_MALFORMED");
    }

    @Test
    @DisplayName("an unreachable consent service still fails closed")
    void failsClosedOnTransportError() {
        server.expect(requestTo(containsString("/v1/consent/evaluate"))).andRespond(withServerError());

        ConsentDecision d = client.evaluateConsent(TENANT, "Patient", "CPID-9", "actor-1", "TREATMENT");

        assertThat(d.permitted()).isFalse();
        assertThat(d.reason()).isEqualTo("CONSENT_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("a denial from the producer is carried through with its reason intact")
    void carriesDenialReason() {
        server.expect(requestTo(containsString("/v1/consent/evaluate")))
                .andRespond(withSuccess(
                        envelope("{\"permitted\":false,\"reason\":\"CONSENT_WITHDRAWN\"}"),
                        MediaType.APPLICATION_JSON));

        ConsentDecision d = client.evaluateConsent(TENANT, "Patient", "CPID-9", "actor-1", "TREATMENT");

        assertThat(d.permitted()).isFalse();
        assertThat(d.reason()).isEqualTo("CONSENT_WITHDRAWN");
    }
}
