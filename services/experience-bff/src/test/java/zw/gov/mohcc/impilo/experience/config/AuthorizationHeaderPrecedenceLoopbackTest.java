package zw.gov.mohcc.impilo.experience.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.auth.ServiceTokenProperties;
import zw.gov.mohcc.impilo.experience.auth.ServiceTokenProvider;
import zw.gov.mohcc.impilo.experience.client.DaidzaiServiceClient;
import zw.gov.mohcc.impilo.experience.client.KeycloakAdminClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Authorization precedence, proved at the transport layer.
 *
 * <p>Until 2026-07-27 the trust-forwarding interceptor copied the inbound Authorization with a
 * plain {@code set()}. Interceptors run <em>after</em> the client method has built its headers, so
 * a deliberately pre-set bearer — a Keycloak admin token, or the BFF's own service-account token
 * on a pre-auth/public lane — was silently replaced by whatever token the caller happened to be
 * holding. The actor context those same methods set was NOT replaced (it already used
 * only-if-absent), so a single outbound request could assert {@code X-Actor-Type: SYSTEM} while
 * carrying a citizen's token: a confused deputy assembled inside one call.</p>
 *
 * <p><strong>Why a real loopback and not {@code MockRestServiceServer}.</strong> Binding
 * MockRestServiceServer to a RestTemplate <em>replaces its request factory</em>. It observes the
 * request object the interceptor chain produced, not bytes that crossed a socket, and the BFF has
 * already shipped one estate-wide defect ({@code SimpleClientHttpRequestFactory} being unable to
 * send PATCH) that was invisible for exactly that reason. Every assertion here is on what a real
 * HTTP server actually received.</p>
 */
class AuthorizationHeaderPrecedenceLoopbackTest {

    /** The care-plane tenant an authenticated clinical caller carries. */
    private static final String CARE_TENANT = PublicTenants.CARE_PLANE;

    private HttpServer server;
    private String baseUrl;
    /** Headers as the downstream really received them, keyed by request path. */
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
        server.stop(0);
    }

    // ── (a) a deliberate pre-set survives an inbound user token ──────────────────────────

    /**
     * The defect, at its narrowest. A client method pre-sets its own bearer while a signed-in
     * user's request is in flight; the token that reaches the downstream must be the pre-set one.
     *
     * <p>Before the fix the downstream received {@code Bearer citizen-token}.</p>
     */
    @Test
    void preSetAuthorizationSurvivesAnInboundUserToken() {
        signedInCitizenRequestIsInFlight();
        RestTemplate template = interceptedTemplate();

        org.springframework.http.HttpHeaders preSet = new org.springframework.http.HttpHeaders();
        preSet.set(CompanionHeaders.AUTHORIZATION, "Bearer deliberate-service-token");
        template.exchange(baseUrl + "/deliberate", HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(preSet), String.class);

        assertEquals("Bearer deliberate-service-token", header("/deliberate", CompanionHeaders.AUTHORIZATION),
                "a bearer the client method deliberately pre-set must reach the downstream; "
                        + "the interceptor runs after the client builds its headers and must not overwrite it");
    }

    /**
     * The same seam through the real {@link KeycloakAdminClient}, whose admin bearer was being
     * replaced by the caller's token on every Keycloak Admin API call made inside a user request.
     * Also pins task 2: the IdP template is NOT intercepted, so no Impilo trust header reaches the
     * identity provider.
     */
    @Test
    void keycloakAdminCallsCarryTheAdminTokenAndNoImpiloTrustHeaders() {
        signedInCitizenRequestIsInFlight();
        KeycloakAdminClient client = new KeycloakAdminClient(new ServiceClientConfig().idpRestTemplate());
        ReflectionTestUtils.setField(client, "keycloakUrl", baseUrl);
        ReflectionTestUtils.setField(client, "realm", "impilo");
        ReflectionTestUtils.setField(client, "userAdminClientId", "impilo-user-admin");
        ReflectionTestUtils.setField(client, "userAdminSecret", "s3cret");

        // The loopback answers {"ok":true}, so there is no access_token to extract and the client
        // returns null — irrelevant here. What is under test is what crossed the socket.
        client.setUserEnabled("user-77", false);

        String tokenPath = "/realms/impilo/protocol/openid-connect/token";
        assertNotNull(received.get(tokenPath), "the client must have called the Keycloak token endpoint");
        assertNull(header(tokenPath, CompanionHeaders.AUTHORIZATION),
                "the call that MINTS the admin token must not carry the caller's user token");
        assertNull(header(tokenPath, CompanionHeaders.TENANT_ID),
                "Keycloak is not a sovereign Impilo service — trust headers are meaningless there");
        assertNull(header(tokenPath, CompanionHeaders.ACTOR_TYPE),
                "Keycloak is not a sovereign Impilo service — trust headers are meaningless there");
        assertNull(header(tokenPath, CompanionHeaders.ACCESS_MODE),
                "the interceptor's unconditional X-Access-Mode stamp proves it ran — it must not have");
    }

    // ── (b) blast radius: with nothing pre-set, forwarding is unchanged ──────────────────

    /**
     * The other half of the change. Only-if-absent must be indistinguishable from unconditional
     * forwarding wherever no client method pre-set anything — which is every ordinary BFF call.
     * If this ever fails, JWT propagation has been broken estate-wide.
     */
    @Test
    void inboundUserTokenIsStillForwardedWhenNothingIsPreSet() {
        signedInCitizenRequestIsInFlight();
        RestTemplate template = interceptedTemplate();

        template.getForEntity(baseUrl + "/ordinary", String.class);

        assertEquals("Bearer citizen-token", header("/ordinary", CompanionHeaders.AUTHORIZATION),
                "with no pre-set bearer the inbound user token must be forwarded unchanged");
        assertEquals("citizen-1", header("/ordinary", CompanionHeaders.ACTOR_ID));
        assertEquals("CITIZEN", header("/ordinary", CompanionHeaders.ACTOR_TYPE));
        assertEquals("tenant-1", header("/ordinary", CompanionHeaders.TENANT_ID));
    }

    /** And the service token still fills the gap when there is neither a pre-set nor an inbound one. */
    @Test
    void mintedServiceTokenStillFillsTheGapWhenThereIsNeither() {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(CompanionHeaders.TENANT_ID, "tenant-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));

        interceptedTemplate("minted-svc-token").getForEntity(baseUrl + "/anonymous", String.class);

        assertEquals("Bearer minted-svc-token", header("/anonymous", CompanionHeaders.AUTHORIZATION),
                "the S2S fallback must be unaffected: it is gated on the header being absent, "
                        + "and it must never override either of the tiers above it");
    }

    // ── (c) the confused deputy: SYSTEM actor and service bearer stay consistent ─────────

    /**
     * The finding that makes this a security fix rather than a tidy-up, proved through the real
     * client that carries it.
     *
     * <p>{@link DaidzaiServiceClient#createPublicSosRequest} asserts {@code X-Actor-Type: SYSTEM}
     * and {@code X-Actor-ID: public-gateway} alongside the BFF's own service bearer. The public
     * lane is anonymous by design, but nothing prevents a signed-in user from hitting a public
     * endpoint — the shell attaches Authorization to every call it makes — and before the fix that
     * flipped the request into a confused deputy: SYSTEM authority, citizen's token, one request.
     * Daidzai would authenticate the citizen and read SYSTEM as the actor.</p>
     *
     * <p>The assertion is on the pair, not on the bearer alone: the defect is the two halves
     * disagreeing.</p>
     */
    @Test
    void systemActorAndServiceBearerReachDaidzaiTogether() {
        signedInCitizenRequestIsInFlight();
        DaidzaiServiceClient daidzai = new DaidzaiServiceClient(
                interceptedTemplate(), ServiceClientConfig.testServiceEndpoints(baseUrl));

        daidzai.createPublicSosRequest(Map.of("description", "collapsed at the rank"), "bff-service-token");

        String path = "/internal/v1/daidzai/requests";
        assertEquals("SYSTEM", header(path, CompanionHeaders.ACTOR_TYPE));
        assertEquals("public-gateway", header(path, CompanionHeaders.ACTOR_ID));
        assertEquals("Bearer bff-service-token", header(path, CompanionHeaders.AUTHORIZATION),
                "SYSTEM actor context and the token authenticating it must describe the SAME caller; "
                        + "a citizen's token arriving under X-Actor-Type: SYSTEM is a confused deputy");
    }

    /**
     * The unauthenticated public lane must be untouched — this is the case that used to be safe
     * only by accident, and it has to stay safe on purpose.
     */
    @Test
    void anonymousPublicSosStillCarriesTheServiceBearer() {
        MockHttpServletRequest anonymous = new MockHttpServletRequest();
        anonymous.addHeader(CompanionHeaders.TENANT_ID, "tenant-public");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(anonymous));
        DaidzaiServiceClient daidzai = new DaidzaiServiceClient(
                interceptedTemplate(), ServiceClientConfig.testServiceEndpoints(baseUrl));

        daidzai.createPublicSosRequest(Map.of("description", "rtc on the highway"), "bff-service-token");

        String path = "/internal/v1/daidzai/requests";
        assertEquals("SYSTEM", header(path, CompanionHeaders.ACTOR_TYPE));
        assertEquals("Bearer bff-service-token", header(path, CompanionHeaders.AUTHORIZATION));
    }

    // ── tenant plane declaration (two-plane model) ───────────────────────────────────────

    /**
     * A client declaring the REGISTRY plane keeps it under an authenticated CARE-plane caller.
     *
     * <p>Facility/provider/geo masters live on the registry plane. A care-plane clinician asking a
     * facility question is a genuine cross-plane read, so {@code TusoServiceClient} declares
     * REGISTRY — and before tenant was forwarded only-if-absent, the interceptor overwrote that
     * with the caller's care-plane tenant, tuso's {@code tenant_id} filter matched nothing, and
     * birth-destination returned 502 UNAVAILABLE.</p>
     *
     * <p>This is safe because tenant is already client-supplied and unvalidated
     * (sessionStorage → header → TrustContextFilter, no claim check); this narrows the override to
     * BFF code and the PublicTenants constants. See ServiceClientConfig for the full argument.</p>
     */
    @Test
    void clientDeclaredRegistryPlaneSurvivesAnAuthenticatedCarePlaneCaller() {
        carePlaneClinicianRequestIsInFlight();
        var tuso = new zw.gov.mohcc.impilo.experience.client.TusoServiceClient(
                interceptedTemplate(), ServiceClientConfig.testServiceEndpoints(baseUrl));

        tuso.getFacilityStatusSummary(1L);

        String path = "/v1/internal/facilities/1/status-summary";
        assertEquals(PublicTenants.REGISTRY_PLANE, header(path, CompanionHeaders.TENANT_ID),
                "a client's deliberate plane declaration must reach the downstream; the caller's "
                        + "care-plane tenant finds no facility rows and surfaces as a 502");
    }

    /**
     * The other direction, and the one that bounds the blast radius: a client that declares nothing
     * still sends the caller's own tenant, unchanged. Every ordinary BFF call is this case, so if
     * this ever fails, tenant scoping has been broken estate-wide.
     */
    @Test
    void callerTenantIsStillForwardedWhenNoPlaneIsDeclared() {
        carePlaneClinicianRequestIsInFlight();

        interceptedTemplate().getForEntity(baseUrl + "/ordinary-tenant", String.class);

        assertEquals(CARE_TENANT, header("/ordinary-tenant", CompanionHeaders.TENANT_ID),
                "with no declared plane the caller's tenant must be forwarded unchanged — "
                        + "only-if-absent must be indistinguishable from forward here");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────

    /** An authenticated CARE-plane clinician's request — the case that overwrote a plane declaration. */
    private void carePlaneClinicianRequestIsInFlight() {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(CompanionHeaders.TENANT_ID, CARE_TENANT);
        inbound.addHeader(CompanionHeaders.POD_ID, "pod-1");
        inbound.addHeader(CompanionHeaders.AUTHORIZATION, "Bearer clinician-token");
        inbound.addHeader(CompanionHeaders.ACTOR_TYPE, "PROVIDER");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));
    }

    /** A signed-in citizen's request is on the stack — the condition under which the defect bit. */
    private void signedInCitizenRequestIsInFlight() {
        MockHttpServletRequest inbound = new MockHttpServletRequest();
        inbound.addHeader(CompanionHeaders.TENANT_ID, "tenant-1");
        inbound.addHeader(CompanionHeaders.POD_ID, "pod-1");
        inbound.addHeader(CompanionHeaders.AUTHORIZATION, "Bearer citizen-token");
        inbound.addHeader(CompanionHeaders.ACTOR_ID, "citizen-1");
        inbound.addHeader(CompanionHeaders.ACTOR_TYPE, "CITIZEN");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(inbound));
    }

    private RestTemplate interceptedTemplate() {
        return interceptedTemplate(null);
    }

    private RestTemplate interceptedTemplate(String mintedToken) {
        ServiceClientConfig config = new ServiceClientConfig();
        return config.serviceRestTemplate(config.trustHeaderForwardingInterceptor(provider(mintedToken),
                new VisibilityPropagationShadowReporter(new ObjectMapper()),
                new VisibilityObligationPropagator(false)));
    }

    private String header(String path, String name) {
        Map<String, String> headers = received.get(path);
        assertNotNull(headers, "the downstream never received a request for " + path
                + " (recorded: " + received.keySet() + ")");
        // com.sun.net.httpserver.Headers canonicalises header names (X-Tenant-Id), so match case-insensitively.
        return headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static ServiceTokenProvider provider(String token) {
        return new ServiceTokenProvider(
                new ServiceTokenProperties(false, null, null, null, null), new ObjectMapper()) {
            @Override
            public Optional<String> getAccessToken() {
                if (token == null) {
                    throw new AssertionError("ServiceTokenProvider must not be consulted when an "
                            + "Authorization header already exists (pre-set or forwarded)");
                }
                return Optional.of(token);
            }
        };
    }
}
