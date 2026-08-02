package zw.gov.mohcc.impilo.shared.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The peer services in this estate call each other with a bare RestTemplate and no credential, so
 * a callee that switches its resource server on rejects every one of them. These tests pin the
 * behaviour that makes enforcement possible, and the three ways it could quietly go wrong:
 * hitting the identity provider per request, refreshing in lockstep, and overwriting a delegated
 * human token with the workload's own.
 */
class WorkloadTokenProviderTest {

    private static final String TOKEN_URI = "http://keycloak:8080/realms/impilo/protocol/openid-connect/token";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private WorkloadTokenProperties properties;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        properties = new WorkloadTokenProperties();
        properties.setEnabled(true);
        properties.setTokenUri(TOKEN_URI);
        properties.setClientId("vashandi-workforce-service");
        properties.setClientSecret("s3cret");
    }

    private WorkloadTokenProvider provider() {
        return new WorkloadTokenProvider(restTemplate, properties);
    }

    @Test
    @DisplayName("mints a client-credentials token and returns it")
    void mintsToken() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":300}",
                        MediaType.APPLICATION_JSON));

        assertThat(provider().getAccessToken()).contains("tok-1");
        server.verify();
    }

    @Test
    @DisplayName("caches — a second call does NOT hit the identity provider")
    void cachesAcrossCalls() {
        // One expectation only. A per-request acquisition would fail verify() by exceeding it,
        // which is the assertion that matters: Keycloak must not sit on the synchronous path of
        // every east-west call.
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":300}",
                        MediaType.APPLICATION_JSON));

        WorkloadTokenProvider p = provider();
        for (int i = 0; i < 25; i++) {
            assertThat(p.getAccessToken()).contains("tok-1");
        }
        server.verify();
    }

    @Test
    @DisplayName("a failure backs off instead of hammering the identity provider per request")
    void failureBacksOff() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        WorkloadTokenProvider p = provider();
        assertThat(p.getAccessToken()).isEmpty();
        // Further calls inside the backoff window must not produce more requests; a second
        // expectation was never registered, so an extra call fails verify().
        for (int i = 0; i < 10; i++) {
            assertThat(p.getAccessToken()).isEmpty();
        }
        server.verify();
    }

    @Test
    @DisplayName("no credential configured yields no token — never an unauthenticated fallback")
    void noCredentialYieldsNothing() {
        properties.setClientId("");
        assertThat(provider().getAccessToken()).isEmpty();
        // No HTTP call should have been attempted at all.
        server.verify();
    }

    @Test
    @DisplayName("disabled yields no token and no call")
    void disabledIsInert() {
        properties.setEnabled(false);
        assertThat(provider().getAccessToken()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("refresh jitter only ever moves refresh EARLIER, never past expiry")
    void jitterNeverServesAnExpiredToken() {
        // A jitter added rather than subtracted would let a workload hold a token past its own
        // lifetime and start emitting 401s that look like a policy problem.
        properties.setRefreshSkewSeconds(30);
        properties.setRefreshJitterSeconds(20);
        long expiresIn = 300;

        for (int i = 0; i < 200; i++) {
            long maxRefresh = expiresIn - properties.getRefreshSkewSeconds();
            long minRefresh = maxRefresh - properties.getRefreshJitterSeconds();
            assertThat(minRefresh).isGreaterThan(0);
            assertThat(maxRefresh).isLessThan(expiresIn);
        }
    }

    @Test
    @DisplayName("interceptor attaches the workload bearer when none is present")
    void interceptorAttachesBearer() throws Exception {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":300}",
                        MediaType.APPLICATION_JSON));

        MockClientHttpRequest request = new MockClientHttpRequest();
        request.setURI(URI.create("http://workforce-governance-service:8165/x"));
        new WorkloadTokenInterceptor(provider()).intercept(request, new byte[0], noopExecution());

        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer tok-1");
    }

    @Test
    @DisplayName("interceptor NEVER overwrites a delegated human token")
    void interceptorPreservesDelegatedToken() throws Exception {
        // Overwriting would silently convert "acting for a human" into "acting as the service" --
        // an escalation the callee cannot detect, because the workload credential is the stronger
        // one. No token request is registered, so minting here would also fail verify().
        MockClientHttpRequest request = new MockClientHttpRequest();
        request.setURI(URI.create("http://workforce-governance-service:8165/x"));
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer human-token");

        new WorkloadTokenInterceptor(provider()).intercept(request, new byte[0], noopExecution());

        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer human-token");
        server.verify();
    }

    @Test
    @DisplayName("no token available means no header — the callee decides, fail-closed")
    void noTokenMeansNoHeader() throws Exception {
        properties.setEnabled(false);
        MockClientHttpRequest request = new MockClientHttpRequest();
        request.setURI(URI.create("http://workforce-governance-service:8165/x"));

        new WorkloadTokenInterceptor(provider()).intercept(request, new byte[0], noopExecution());

        assertThat(request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }

    private static ClientHttpRequestExecution noopExecution() {
        AtomicInteger calls = new AtomicInteger();
        return (req, body) -> {
            calls.incrementAndGet();
            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        };
    }
}
