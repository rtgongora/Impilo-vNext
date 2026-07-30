package zw.gov.mohcc.impilo.experience.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.auth.ServiceTokenProperties;
import zw.gov.mohcc.impilo.experience.auth.ServiceTokenProvider;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import jakarta.servlet.http.HttpServletRequest;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Server-authoritative tenant, proved at the transport layer.
 *
 * <p>{@code X-Tenant-ID} is chosen by the browser and consumed raw by every downstream trust
 * filter, so the boundary every service filters on is client-supplied. {@link TenantContextFilter}
 * gives tenant the treatment {@link ActorContextFilter} gave actor. The two things that must hold
 * together are in tension, which is why they are tested in the same rig:</p>
 * <ol>
 *   <li>a tenant a caller <em>claims</em> in a header must not reach the downstream, and</li>
 *   <li>a plane a BFF client <em>declares</em> in code must still reach it — the two-plane model
 *       depends on care-plane clinicians reading registry-plane rows.</li>
 * </ol>
 *
 * <p><strong>Why a real loopback and not {@code MockRestServiceServer}.</strong> Binding
 * MockRestServiceServer to a RestTemplate <em>replaces its request factory</em>, so it observes the
 * request object the interceptor chain built rather than bytes that crossed a socket. This BFF has
 * already shipped one estate-wide defect ({@code SimpleClientHttpRequestFactory} being unable to
 * send PATCH) that was invisible for exactly that reason. Every assertion here is on what a real
 * HTTP server actually received, downstream of both the servlet filter and the outbound
 * interceptor — the two layers whose interaction is the whole design.</p>
 */
class TenantAuthorityLoopbackTest {

    private static final String FORGED_TENANT = "11111111-2222-3333-4444-555555555555";

    private HttpServer server;
    private String baseUrl;
    private final Map<String, Map<String, String>> received = new ConcurrentHashMap<>();

    @BeforeEach
    void startLoopback() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders()
                    .forEach((name, values) -> headers.put(name, values.isEmpty() ? "" : values.get(0)));
            received.put(exchange.getRequestURI().getPath(), headers);

            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopLoopbackAndClearContext() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        server.stop(0);
    }

    // ── (1) a tenant the caller CLAIMS must not reach the downstream ─────────────────────

    /**
     * The hole, at its narrowest. A signed-in caller sets {@code X-Tenant-ID} to a tenant that is
     * not theirs — trivially, from the browser console, since the shell reads it out of
     * sessionStorage — and today it is forwarded verbatim to services that filter their reads and
     * writes on exactly that value.
     */
    @Test
    void aForgedTenantHeaderNeverReachesTheDownstream() {
        authenticatedCallerPresenting(FORGED_TENANT, "ENFORCE");

        interceptedTemplate().getForEntity(baseUrl + "/ordinary", String.class);

        assertEquals(PublicTenants.CARE_PLANE, header("/ordinary", CompanionHeaders.TENANT_ID),
                "the tenant that crossed the socket must be the one the server resolved, not the "
                        + "one the caller typed; every downstream filters its rows on this value");
    }

    /** A tenant claim on the token, once one exists, outranks the configured default. */
    @Test
    void aTenantClaimOnTheTokenOutranksTheConfiguredDefault() {
        TenantAuthorityProperties props = properties("ENFORCE");
        MockHttpServletRequest inbound = inboundRequest("/internal/v1/anything", FORGED_TENANT);
        authenticateWith(Map.of("health_id", "person-1", "tenant_id", PublicTenants.REGISTRY_PLANE));
        runFilterAndPublish(inbound, props);

        interceptedTemplate().getForEntity(baseUrl + "/claimed", String.class);

        assertEquals(PublicTenants.REGISTRY_PLANE, header("/claimed", CompanionHeaders.TENANT_ID),
                "when the token carries a tenant claim it is the authority — the configured "
                        + "default is only the fallback for tokens that carry none");
    }

    // ── (2) a plane a BFF client DECLARES must still reach it ────────────────────────────

    /**
     * The constraint that makes the naive fix wrong. Facility/provider/geo masters are
     * registry-plane; a care-plane clinician asking a facility question is a legitimate
     * cross-plane read, and {@code TusoServiceClient} declares REGISTRY at the call site.
     *
     * <p>This survives because the two mechanisms are structurally distinct: a caller's claim is an
     * <em>inbound header</em> (which the filter rewrites), while a plane declaration is pre-set on
     * the <em>outbound</em> headers from a {@link PublicTenants} constant, and the interceptor
     * forwards the inbound tenant only-if-absent. A design that overrode the outbound value would
     * pass test (1) and fail here — which is the point of testing both in one rig.</p>
     */
    @Test
    void aClientDeclaredRegistryPlaneStillResolvesUnderEnforcement() {
        authenticatedCallerPresenting(PublicTenants.CARE_PLANE, "ENFORCE");
        TusoServiceClient tuso = new TusoServiceClient(
                interceptedTemplate(), ServiceClientConfig.testServiceEndpoints(baseUrl));

        tuso.getFacilityStatusSummary(1L);

        assertEquals(PublicTenants.REGISTRY_PLANE,
                header("/v1/internal/facilities/1/status-summary", CompanionHeaders.TENANT_ID),
                "a cross-plane read declared by BFF code must survive enforcement; if it does not, "
                        + "a care-plane clinician can no longer read facilities at all (502)");
    }

    // ── (3) the lanes that must keep working ────────────────────────────────────────────

    /**
     * SHADOW must be observably inert. If this test can be made to pass by an enforcing filter,
     * the ladder is theatre and "we ran it in shadow first" proves nothing.
     */
    @Test
    void shadowModeForwardsTheCallersTenantUnchanged() {
        authenticatedCallerPresenting(FORGED_TENANT, "SHADOW");

        interceptedTemplate().getForEntity(baseUrl + "/shadowed", String.class);

        assertEquals(FORGED_TENANT, header("/shadowed", CompanionHeaders.TENANT_ID),
                "SHADOW observes and audits but must not change what crosses the socket");
    }

    /**
     * Anonymous lanes carry no token, so there is no authority to derive a tenant from. They must
     * pass through untouched rather than be forced onto the authenticated default.
     */
    @Test
    void anAnonymousRequestIsLeftUntouched() {
        MockHttpServletRequest inbound = inboundRequest("/internal/v1/anything", "tenant-public");
        // deliberately no authentication
        runFilterAndPublish(inbound, properties("ENFORCE"));

        interceptedTemplate().getForEntity(baseUrl + "/anon", String.class);

        assertEquals("tenant-public", header("/anon", CompanionHeaders.TENANT_ID),
                "a token-derived tenant cannot be required where there is no token");
    }

    /**
     * {@link PublicGatewayAnonymousDefaultsFilter} deliberately forces the registry plane on the
     * public namespace <em>including for signed-in callers</em> ("the caller's tenant is never
     * authoritative here"), because a signed-in browser's care-plane tenant otherwise drives public
     * lookups against the wrong plane and they 502. This filter runs later and must not undo it.
     */
    @Test
    void thePublicGatewayLaneKeepsItsOwnTenantOverride() {
        MockHttpServletRequest inbound =
                inboundRequest("/internal/v1/public/gateway/facilities", PublicTenants.REGISTRY_PLANE);
        authenticateWith(Map.of("health_id", "person-1"));
        runFilterAndPublish(inbound, properties("ENFORCE"));

        interceptedTemplate().getForEntity(baseUrl + "/public-lane", String.class);

        assertEquals(PublicTenants.REGISTRY_PLANE, header("/public-lane", CompanionHeaders.TENANT_ID),
                "the public gateway's own override owns this namespace; forcing the authenticated "
                        + "default here would break public pages for exactly the signed-in users "
                        + "that override was written for");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────

    /** A signed-in caller presenting {@code presentedTenant}, with the filter run at {@code mode}. */
    private void authenticatedCallerPresenting(String presentedTenant, String mode) {
        MockHttpServletRequest inbound = inboundRequest("/internal/v1/anything", presentedTenant);
        authenticateWith(Map.of("health_id", "person-1"));
        runFilterAndPublish(inbound, properties(mode));
    }

    private static MockHttpServletRequest inboundRequest(String uri, String tenant) {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.setRequestURI(uri);
        inbound.addHeader(CompanionHeaders.TENANT_ID, tenant);
        inbound.addHeader(CompanionHeaders.POD_ID, "pod-1");
        inbound.addHeader(CompanionHeaders.AUTHORIZATION, "Bearer caller-token");
        return inbound;
    }

    private static void authenticateWith(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none");
        claims.forEach(builder::claim);
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(builder.build()));
    }

    /**
     * Run the request through the real filter and publish whatever it produced as the in-flight
     * request — exactly what the DispatcherServlet does, and what the outbound interceptor reads
     * via {@code RequestContextHolder}.
     */
    private static void runFilterAndPublish(MockHttpServletRequest inbound,
                                            TenantAuthorityProperties properties) {
        try {
            new TenantContextFilter(properties).doFilter(inbound, new MockHttpServletResponse(),
                    (req, res) -> RequestContextHolder.setRequestAttributes(
                            new ServletRequestAttributes((HttpServletRequest) req)));
        } catch (Exception e) {
            throw new IllegalStateException("filter chain failed", e);
        }
    }

    private static TenantAuthorityProperties properties(String mode) {
        TenantAuthorityProperties properties = new TenantAuthorityProperties();
        properties.setMode(mode);
        return properties;
    }

    private RestTemplate interceptedTemplate() {
        ServiceClientConfig config = new ServiceClientConfig();
        return config.serviceRestTemplate(config.trustHeaderForwardingInterceptor(
                new ServiceTokenProvider(
                        new ServiceTokenProperties(false, null, null, null, null), new ObjectMapper()) {
                    @Override
                    public Optional<String> getAccessToken() {
                        return Optional.empty();
                    }
                },
                new VisibilityPropagationShadowReporter(new ObjectMapper()),
                new VisibilityObligationPropagator(false)));
    }

    private String header(String path, String name) {
        Map<String, String> headers = received.get(path);
        assertNotNull(headers, "the downstream never received a request for " + path
                + " (recorded: " + received.keySet() + ")");
        // com.sun.net.httpserver.Headers canonicalises header names (X-Tenant-Id).
        return headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
