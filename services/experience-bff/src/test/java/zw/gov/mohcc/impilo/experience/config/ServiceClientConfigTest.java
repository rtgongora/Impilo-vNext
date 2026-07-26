package zw.gov.mohcc.impilo.experience.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.auth.ServiceTokenProperties;
import zw.gov.mohcc.impilo.experience.auth.ServiceTokenProvider;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ServiceClientConfigTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void trustForwarderCopiesActorAndOperationalContextHeaders() throws IOException {
        ServiceClientConfig config = new ServiceClientConfig();
        HttpServletRequest inbound = requestWithHeaders();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes((MockHttpServletRequest) inbound));

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("http://example.test"));
        config.trustHeaderForwardingInterceptor(providerNeverConsulted()).intercept(outbound, new byte[0], (request, body) ->
                new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertEquals("tenant-1", outbound.getHeaders().getFirst(CompanionHeaders.TENANT_ID));
        assertEquals("pod-1", outbound.getHeaders().getFirst(CompanionHeaders.POD_ID));
        assertEquals("req-1", outbound.getHeaders().getFirst(CompanionHeaders.REQUEST_ID));
        assertEquals("corr-1", outbound.getHeaders().getFirst(CompanionHeaders.CORRELATION_ID));
        assertEquals("Bearer token", outbound.getHeaders().getFirst(CompanionHeaders.AUTHORIZATION));
        assertEquals("user-1", outbound.getHeaders().getFirst(CompanionHeaders.ACTOR_ID));
        assertEquals("PROVIDER", outbound.getHeaders().getFirst(CompanionHeaders.ACTOR_TYPE));
        assertEquals("TREATMENT", outbound.getHeaders().getFirst(CompanionHeaders.PURPOSE_OF_USE));
        assertEquals("facility-1", outbound.getHeaders().getFirst(CompanionHeaders.FACILITY_ID));
        assertEquals("workspace-1", outbound.getHeaders().getFirst(CompanionHeaders.WORKSPACE_ID));
        assertEquals("shift-1", outbound.getHeaders().getFirst(CompanionHeaders.SHIFT_ID));
        assertEquals("idem-1", outbound.getHeaders().getFirst(CompanionHeaders.IDEMPOTENCY_KEY));
        assertEquals("5000", outbound.getHeaders().getFirst(CompanionHeaders.CLIENT_TIMEOUT_MS));
    }

    /**
     * The clinical episode correlation id must survive the BFF hop.
     *
     * <p>Regression test for a live defect: the shell set {@code X-Trauma-Episode-ID} on every
     * resuscitation, ED and blood write and pct/inpatient/madi all read it, but the BFF never
     * forwarded it — so every resus event reached inpatient-service unstamped and the cross-service
     * episode timeline was assembled from nothing. The pre-existing shell-side test asserted only
     * that the shell SET the header, which is exactly why the gap survived review.
     */
    @Test
    void trustForwarderCopiesTraumaEpisodeCorrelationId() throws IOException {
        ServiceClientConfig config = new ServiceClientConfig();
        MockHttpServletRequest inbound = (MockHttpServletRequest) requestWithHeaders();
        inbound.addHeader(CompanionHeaders.TRAUMA_EPISODE_ID, "episode-77");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.POST, java.net.URI.create("http://example.test"));
        config.trustHeaderForwardingInterceptor(providerNeverConsulted()).intercept(outbound, new byte[0], (request, body) ->
                new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertEquals("episode-77", outbound.getHeaders().getFirst(CompanionHeaders.TRAUMA_EPISODE_ID));
    }

    /**
     * The BFF forwards the episode id, it never invents one. An absent correlation id must stay
     * absent so a downstream service can tell "no episode" from "some episode the BFF guessed".
     */
    @Test
    void trustForwarderDoesNotSynthesizeAnAbsentEpisodeId() throws IOException {
        ServiceClientConfig config = new ServiceClientConfig();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes((MockHttpServletRequest) requestWithHeaders()));

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.POST, java.net.URI.create("http://example.test"));
        config.trustHeaderForwardingInterceptor(providerNeverConsulted()).intercept(outbound, new byte[0], (request, body) ->
                new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertNull(outbound.getHeaders().getFirst(CompanionHeaders.TRAUMA_EPISODE_ID));
    }

    // ── Service workload credential (preview==prod auth wave, stage 2 slice 1) ──────────
    //
    // Trust headers are NOT authentication (confused-deputy finding 2026-07-22): when there
    // is no inbound user token the interceptor must attach the BFF's OWN client_credentials
    // token; when there IS one, JWT propagation is unchanged and the provider is never asked.
    // These tests deliberately exercise the real interceptor seam — the prior failure mode
    // was unit tests mocking the client and thereby mocking away the downstream 401.

    /** Service-originated call with NO request context (scheduled job / Kafka consumer). */
    @Test
    void serviceTokenAttachedWhenThereIsNoRequestContextAtAll() throws IOException {
        ServiceClientConfig config = new ServiceClientConfig();
        // RequestContextHolder deliberately left empty.

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("http://ndila"));
        config.trustHeaderForwardingInterceptor(providerYielding("svc-token-1")).intercept(outbound, new byte[0],
                (request, body) -> new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertEquals("Bearer svc-token-1", outbound.getHeaders().getFirst(CompanionHeaders.AUTHORIZATION));
    }

    /** Public-lane composition: request context exists but carries no Authorization header. */
    @Test
    void serviceTokenAttachedWhenInboundRequestHasNoUserToken() throws IOException {
        ServiceClientConfig config = new ServiceClientConfig();
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(CompanionHeaders.TENANT_ID, "tenant-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("http://ndila"));
        config.trustHeaderForwardingInterceptor(providerYielding("svc-token-2")).intercept(outbound, new byte[0],
                (request, body) -> new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertEquals("Bearer svc-token-2", outbound.getHeaders().getFirst(CompanionHeaders.AUTHORIZATION));
        assertEquals("tenant-1", outbound.getHeaders().getFirst(CompanionHeaders.TENANT_ID));
    }

    /** An inbound user token wins: forwarded unchanged, provider NEVER consulted. */
    @Test
    void inboundUserTokenIsForwardedUnchangedAndProviderNotConsulted() throws IOException {
        ServiceClientConfig config = new ServiceClientConfig();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes((MockHttpServletRequest) requestWithHeaders()));

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("http://ndila"));
        // providerNeverConsulted() throws AssertionError if getAccessToken() is invoked.
        config.trustHeaderForwardingInterceptor(providerNeverConsulted()).intercept(outbound, new byte[0],
                (request, body) -> new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertEquals("Bearer token", outbound.getHeaders().getFirst(CompanionHeaders.AUTHORIZATION));
    }

    /** An Authorization header pre-set by the client method also wins over the provider. */
    @Test
    void preSetOutboundAuthorizationWinsOverServiceToken() throws IOException {
        ServiceClientConfig config = new ServiceClientConfig();

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("http://ndila"));
        outbound.getHeaders().set(CompanionHeaders.AUTHORIZATION, "Bearer pre-set");
        config.trustHeaderForwardingInterceptor(providerNeverConsulted()).intercept(outbound, new byte[0],
                (request, body) -> new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertEquals("Bearer pre-set", outbound.getHeaders().getFirst(CompanionHeaders.AUTHORIZATION));
    }

    /**
     * Honest degradation: provider unavailable (secret absent / Keycloak down) → the request
     * still goes out, just WITHOUT an Authorization header. The WARN with the
     * SERVICE_TOKEN_MINT_FAILED marker is asserted in ServiceTokenProviderTest.
     */
    @Test
    void requestProceedsWithoutAuthorizationWhenProviderIsUnavailable() throws IOException {
        ServiceClientConfig config = new ServiceClientConfig();

        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("http://ndila"));
        var response = config.trustHeaderForwardingInterceptor(providerYieldingNothing()).intercept(outbound, new byte[0],
                (request, body) -> new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertNull(outbound.getHeaders().getFirst(CompanionHeaders.AUTHORIZATION));
        assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
    }

    /**
     * End-to-end through the REAL seam: a real {@link ServiceTokenProvider} mints a
     * client_credentials token against a mock Keycloak token endpoint, the real interceptor on
     * the real {@code serviceRestTemplate} attaches it, and the DOWNSTREAM request is asserted
     * to carry it. Positive assertion on authenticated content — a mocked provider (or client)
     * would mock away exactly the 401 this slice exists to fix.
     */
    @Test
    void endToEndRealProviderMintsAgainstMockKeycloakAndDownstreamReceivesBearer() {
        String tokenUri = "http://keycloak.test/realms/impilo/protocol/openid-connect/token";
        org.springframework.web.client.RestTemplate tokenClient = new org.springframework.web.client.RestTemplate();
        org.springframework.test.web.client.MockRestServiceServer keycloak =
                org.springframework.test.web.client.MockRestServiceServer.bindTo(tokenClient).build();
        keycloak.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo(tokenUri))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .string("grant_type=client_credentials&client_id=impilo-backend&client_secret=s3cret"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"access_token\":\"e2e-tok\",\"expires_in\":300}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
        ServiceTokenProvider realProvider = new ServiceTokenProvider(
                new ServiceTokenProperties(true, tokenUri, "impilo-backend", "s3cret", null),
                new ObjectMapper(), tokenClient, java.time.Clock.systemUTC());

        ServiceClientConfig config = new ServiceClientConfig();
        org.springframework.web.client.RestTemplate serviceRestTemplate =
                config.serviceRestTemplate(config.trustHeaderForwardingInterceptor(realProvider));
        org.springframework.test.web.client.MockRestServiceServer downstream =
                org.springframework.test.web.client.MockRestServiceServer.bindTo(serviceRestTemplate).build();
        downstream.expect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .requestTo("http://ndila/internal/v1/ndila/distance-matrix"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .header(CompanionHeaders.AUTHORIZATION, "Bearer e2e-tok"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(
                        "{\"etaSeconds\":642}", org.springframework.http.MediaType.APPLICATION_JSON));

        var response = serviceRestTemplate.getForEntity("http://ndila/internal/v1/ndila/distance-matrix", String.class);

        assertEquals("{\"etaSeconds\":642}", response.getBody());
        keycloak.verify();
        downstream.verify();
    }

    private static ServiceTokenProvider providerYielding(String token) {
        return new StubServiceTokenProvider() {
            @Override
            public Optional<String> getAccessToken() {
                return Optional.of(token);
            }
        };
    }

    private static ServiceTokenProvider providerYieldingNothing() {
        return new StubServiceTokenProvider() {
            @Override
            public Optional<String> getAccessToken() {
                return Optional.empty();
            }
        };
    }

    private static ServiceTokenProvider providerNeverConsulted() {
        return new StubServiceTokenProvider() {
            @Override
            public Optional<String> getAccessToken() {
                throw new AssertionError(
                        "ServiceTokenProvider must not be consulted when an Authorization header already exists");
            }
        };
    }

    private abstract static class StubServiceTokenProvider extends ServiceTokenProvider {
        StubServiceTokenProvider() {
            super(new ServiceTokenProperties(false, null, null, null, null), new ObjectMapper());
        }
    }

    private HttpServletRequest requestWithHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CompanionHeaders.TENANT_ID, "tenant-1");
        request.addHeader(CompanionHeaders.POD_ID, "pod-1");
        request.addHeader(CompanionHeaders.REQUEST_ID, "req-1");
        request.addHeader(CompanionHeaders.CORRELATION_ID, "corr-1");
        request.addHeader(CompanionHeaders.AUTHORIZATION, "Bearer token");
        request.addHeader(CompanionHeaders.ACTOR_ID, "user-1");
        request.addHeader(CompanionHeaders.ACTOR_TYPE, "PROVIDER");
        request.addHeader(CompanionHeaders.PURPOSE_OF_USE, "TREATMENT");
        request.addHeader(CompanionHeaders.FACILITY_ID, "facility-1");
        request.addHeader(CompanionHeaders.WORKSPACE_ID, "workspace-1");
        request.addHeader(CompanionHeaders.SHIFT_ID, "shift-1");
        request.addHeader(CompanionHeaders.IDEMPOTENCY_KEY, "idem-1");
        request.addHeader(CompanionHeaders.CLIENT_TIMEOUT_MS, "5000");
        return request;
    }
}
