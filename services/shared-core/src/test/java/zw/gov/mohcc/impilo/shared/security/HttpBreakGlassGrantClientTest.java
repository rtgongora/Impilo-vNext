package zw.gov.mohcc.impilo.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenProperties;
import zw.gov.mohcc.impilo.shared.auth.WorkloadTokenProvider;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantCheck;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantQuery;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantVerdict;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The three-outcome mapping, which is the only interesting behaviour in this client.
 *
 * <p>The tests that matter are the negative ones: every way of failing to get an answer must map
 * to UNREACHABLE, and none of them may map to NO_GRANT. A client that mapped a 404 or a 401 to
 * NO_GRANT would refuse emergency transfusions over a typo in a values file, and would do it
 * looking exactly like a working control.</p>
 */
class HttpBreakGlassGrantClientTest {

    private static final String BASE_URL = "http://tshepo-authz:8081";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private HttpBreakGlassGrantClient client;

    /** A provider that never yields a token, so tests exercise the inbound-bearer fallback path. */
    private static WorkloadTokenProvider disabledTokenProvider() {
        WorkloadTokenProperties props = new WorkloadTokenProperties();
        props.setEnabled(false);
        return new WorkloadTokenProvider(new RestTemplate(), props);
    }

    private static WorkloadTokenProvider tokenProviderYielding(String token) {
        return new WorkloadTokenProvider(new RestTemplate(), new WorkloadTokenProperties()) {
            @Override
            public java.util.Optional<String> getAccessToken() {
                return java.util.Optional.of(token);
            }
        };
    }

    private static BreakGlassGrantQuery query() {
        return new BreakGlassGrantQuery("00000000-0000-0000-4000-800000000001", "actor-1", null,
                "EMERGENCY", "O_NEG_EMERGENCY_RELEASE", "HID-1");
    }

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new HttpBreakGlassGrantClient(restTemplate, BASE_URL, tokenProviderYielding("workload-token"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextTestSupport.clear();
    }

    // ════════════════════════════════════════════════════════════════════
    // answers
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("200 VALID: mapped to VALID, carrying source and grant reference")
    void validResponseMapsToValid() {
        server.expect(requestTo(BASE_URL + "/v1/break-glass/validate"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer workload-token"))
                .andRespond(withSuccess(
                        "{\"verdict\":\"VALID\",\"source\":\"BREAK_GLASS_REQUEST\",\"grantRef\":\"grant-9\"}",
                        MediaType.APPLICATION_JSON));

        BreakGlassGrantCheck check = client.check(query());

        assertEquals(BreakGlassGrantVerdict.VALID, check.verdict());
        assertEquals("BREAK_GLASS_REQUEST", check.source());
        assertEquals("grant-9", check.grantRef());
        server.verify();
    }

    @Test
    @DisplayName("200 NO_GRANT: mapped to NO_GRANT — the only input that may produce it")
    void noGrantResponseMapsToNoGrant() {
        server.expect(requestTo(BASE_URL + "/v1/break-glass/validate"))
                .andRespond(withSuccess("{\"verdict\":\"NO_GRANT\"}", MediaType.APPLICATION_JSON));

        assertEquals(BreakGlassGrantVerdict.NO_GRANT, client.check(query()).verdict());
        server.verify();
    }

    // ════════════════════════════════════════════════════════════════════
    // every way of not getting an answer
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("401 (expired or missing service credential) is UNREACHABLE, never NO_GRANT")
    void unauthorizedIsUnreachable() {
        server.expect(requestTo(BASE_URL + "/v1/break-glass/validate"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        BreakGlassGrantCheck check = client.check(query());

        // A rejected credential says nothing about whether the actor holds a grant. Reading it as
        // NO_GRANT would turn a credential rotation into refused emergency care.
        assertEquals(BreakGlassGrantVerdict.UNREACHABLE, check.verdict());
        assertNotNull(check.detail());
    }

    @Test
    @DisplayName("404 (wrong base URL or unrouted path) is UNREACHABLE, never NO_GRANT")
    void notFoundIsUnreachable() {
        server.expect(requestTo(BASE_URL + "/v1/break-glass/validate"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(BreakGlassGrantVerdict.UNREACHABLE, client.check(query()).verdict());
    }

    @Test
    @DisplayName("503 is UNREACHABLE")
    void serviceUnavailableIsUnreachable() {
        server.expect(requestTo(BASE_URL + "/v1/break-glass/validate"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertEquals(BreakGlassGrantVerdict.UNREACHABLE, client.check(query()).verdict());
    }

    @Test
    @DisplayName("an empty body is UNREACHABLE")
    void emptyBodyIsUnreachable() {
        server.expect(requestTo(BASE_URL + "/v1/break-glass/validate"))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertEquals(BreakGlassGrantVerdict.UNREACHABLE, client.check(query()).verdict());
    }

    @Test
    @DisplayName("an unrecognised verdict is UNREACHABLE — a contract disagreement is not an answer")
    void unrecognisedVerdictIsUnreachable() {
        server.expect(requestTo(BASE_URL + "/v1/break-glass/validate"))
                .andRespond(withSuccess("{\"verdict\":\"MAYBE\"}", MediaType.APPLICATION_JSON));

        BreakGlassGrantCheck check = client.check(query());

        assertEquals(BreakGlassGrantVerdict.UNREACHABLE, check.verdict());
        assertTrue(check.detail().contains("MAYBE"));
    }

    @Test
    @DisplayName("a missing verdict field is UNREACHABLE, not a silent NO_GRANT")
    void missingVerdictIsUnreachable() {
        server.expect(requestTo(BASE_URL + "/v1/break-glass/validate"))
                .andRespond(withSuccess("{\"source\":\"BREAK_GLASS_REQUEST\"}", MediaType.APPLICATION_JSON));

        assertEquals(BreakGlassGrantVerdict.UNREACHABLE, client.check(query()).verdict());
    }

    @Test
    @DisplayName("an unconfigured base URL is UNREACHABLE and says so, without attempting a call")
    void unconfiguredBaseUrlIsUnreachable() {
        HttpBreakGlassGrantClient unconfigured =
                new HttpBreakGlassGrantClient(restTemplate, "", tokenProviderYielding("t"));

        BreakGlassGrantCheck check = unconfigured.check(query());

        assertEquals(BreakGlassGrantVerdict.UNREACHABLE, check.verdict());
        assertTrue(check.detail().contains("not configured"),
                "the reviewer must be able to tell a misconfiguration from an outage");
        server.verify(); // no request was made
    }

    @Test
    @DisplayName("no credential at all is UNREACHABLE and names the misconfiguration")
    void noCredentialIsUnreachable() {
        HttpBreakGlassGrantClient noCredential =
                new HttpBreakGlassGrantClient(restTemplate, BASE_URL, disabledTokenProvider());

        BreakGlassGrantCheck check = noCredential.check(query());

        assertEquals(BreakGlassGrantVerdict.UNREACHABLE, check.verdict());
        assertTrue(check.detail().contains("no credential"));
        server.verify(); // no request was made
    }

    @Test
    @DisplayName("check() never throws, whatever the transport does")
    void neverThrows() {
        RestTemplate exploding = new RestTemplate();
        MockRestServiceServer explodingServer = MockRestServiceServer.createServer(exploding);
        explodingServer.expect(requestTo(BASE_URL + "/v1/break-glass/validate"))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("connect timed out");
                });

        HttpBreakGlassGrantClient c =
                new HttpBreakGlassGrantClient(exploding, BASE_URL, tokenProviderYielding("t"));

        // No assertThrows: the contract is that there is nothing to catch. A caller forced to
        // write a catch block would write "treat it as no grant" inside it.
        BreakGlassGrantCheck check = c.check(query());
        assertEquals(BreakGlassGrantVerdict.UNREACHABLE, check.verdict());
        assertTrue(check.detail().contains("timed out"));
    }

    /** Keeps the security context clean between tests without importing a Spring test context. */
    private static final class SecurityContextTestSupport {
        static void clear() {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
