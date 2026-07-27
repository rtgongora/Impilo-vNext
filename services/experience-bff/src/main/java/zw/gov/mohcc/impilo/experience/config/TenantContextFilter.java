package zw.gov.mohcc.impilo.experience.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.TreeMap;

/**
 * Establishes a <b>server-authoritative</b> tenant, the way {@link ActorContextFilter} established
 * a server-authoritative actor.
 *
 * <h3>The hole this closes</h3>
 * <p>{@code X-Tenant-ID} is chosen by the browser. {@code api-client.ts} reads it from
 * {@code sessionStorage["exp:tenant_id"]} and falls back to a hardcoded UUID; every downstream
 * builds its trust context straight from the header ({@code TrustContextFilter}, both the
 * shared-core and tshepo-sdk copies) with no claim cross-check anywhere. Tenant is the predicate
 * every service filters on — {@code findByTenantIdAnd…} throughout PCT, a mandatory {@code _tag}
 * filter in BUTANO — so the isolation boundary is presently a client-side convention.
 * {@link ActorContextFilter} closed exactly this class for actor identity, naming it "a spoofing
 * hole (a caller could claim any actor)", and does not mention tenant.</p>
 *
 * <h3>Why plane declarations still work — the distinction that makes this safe</h3>
 * <p>The two-plane model ({@link PublicTenants}) requires a care-plane clinician to read
 * registry-plane facility rows. That would look like tenant-hopping if the two were the same
 * mechanism. They are not, and the separation is structural rather than a rule anyone has to
 * remember:</p>
 * <ul>
 *   <li>A <b>caller's tenant claim</b> arrives as an <em>inbound request header</em>. This filter
 *       rewrites that, and only that.</li>
 *   <li>A <b>plane declaration by BFF code</b> is pre-set on the <em>outbound</em>
 *       {@code HttpHeaders} at the client call site, from a {@link PublicTenants} compile-time
 *       constant. {@code ServiceClientConfig} forwards the inbound tenant only-if-absent, so a
 *       pre-set declaration already wins and never consults the inbound value.</li>
 * </ul>
 * <p>So overriding the inbound header cannot break a cross-plane read: the declaration is not
 * reached through it. What the declaration loses is its ability to be silently <em>widened</em> by
 * a caller who picked a different tenant — which is the point.</p>
 *
 * <h3>What is deliberately left untouched</h3>
 * <ul>
 *   <li><b>Anonymous lanes.</b> No authenticated principal ⇒ no authority ⇒ the request passes
 *       through unchanged. A token-derived tenant cannot be required where there is no token.</li>
 *   <li><b>The public gateway namespace.</b> {@link PublicGatewayAnonymousDefaultsFilter} already
 *       <em>overrides</em> tenant to the registry plane there, deliberately and for signed-in
 *       callers too ("the caller's tenant is never authoritative here"). It runs earlier and is
 *       the authority on that namespace; this filter must not clobber it — a signed-in user
 *       browsing public pages would otherwise start resolving them against the care plane and get
 *       the empty/502 results that override exists to prevent.</li>
 *   <li><b>Service-originated calls with no request context</b> (@Scheduled sweeps, Kafka
 *       consumers) never enter a servlet filter at all.</li>
 * </ul>
 *
 * <h3>Mode</h3>
 * <p>Default SHADOW: resolve, log every divergence under {@link #DIVERGENCE_MARKER}, forward the
 * caller's value unchanged. ENFORCE is a separate, evidence-led decision — see
 * {@link TenantAuthorityProperties#getClaim()} for the honest limit of what this can assert until
 * a tenant claim exists.</p>
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    /** Stable grep target for the shadow-mode measurement. */
    public static final String DIVERGENCE_MARKER = "TENANT_HEADER_DIVERGENCE";

    /**
     * Namespace owned by {@link PublicGatewayAnonymousDefaultsFilter}, which forces the public
     * tenant there for authenticated and anonymous callers alike.
     */
    static final String PUBLIC_GATEWAY_PREFIX = "/internal/v1/public/gateway/";

    private final TenantAuthorityProperties properties;

    public TenantContextFilter(TenantAuthorityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (properties.isOff() || isPublicGatewayLane(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authoritative = authoritativeTenant();
        if (authoritative == null) {
            // Unauthenticated: the anonymous lanes, and every request on a deployment running
            // with allow-anonymous (no JwtDecoder ⇒ no principal ⇒ nothing to be authoritative
            // about). Pass through rather than invent an authority we do not have.
            filterChain.doFilter(request, response);
            return;
        }

        String presented = request.getHeader(CompanionHeaders.TENANT_ID);
        if (!authoritative.equals(presented)) {
            log.warn("{} path={} presented={} authoritative={} source={} enforced={}",
                    DIVERGENCE_MARKER, request.getRequestURI(), presented, authoritative,
                    tenantClaim() != null ? "CLAIM" : "AUTHENTICATED_DEFAULT",
                    properties.isEnforcing());
        }

        if (!properties.isEnforcing()) {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new AuthoritativeTenantRequest(request, authoritative), response);
    }

    private static boolean isPublicGatewayLane(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith(PUBLIC_GATEWAY_PREFIX);
    }

    /** The tenant this request is entitled to, or {@code null} when no principal is authenticated. */
    private String authoritativeTenant() {
        if (currentJwt() == null) {
            return null;
        }
        String claimed = tenantClaim();
        return claimed != null ? claimed : properties.getAuthenticatedDefault();
    }

    private String tenantClaim() {
        Jwt jwt = currentJwt();
        if (jwt == null) {
            return null;
        }
        String value = jwt.getClaimAsString(properties.getClaim());
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Jwt currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken();
        }
        if (auth != null && auth.getPrincipal() instanceof Jwt principal) {
            return principal;
        }
        return null;
    }

    /** Read-only request view whose {@code X-Tenant-ID} is forced to the authoritative value. */
    private static final class AuthoritativeTenantRequest extends HttpServletRequestWrapper {

        private final String tenantId;

        private AuthoritativeTenantRequest(HttpServletRequest request, String tenantId) {
            super(request);
            this.tenantId = tenantId;
        }

        private boolean isTenantHeader(String name) {
            return CompanionHeaders.TENANT_ID.equalsIgnoreCase(name);
        }

        @Override
        public String getHeader(String name) {
            return isTenantHeader(name) ? tenantId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return isTenantHeader(name)
                    ? Collections.enumeration(Collections.singletonList(tenantId))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            java.util.Map<String, Boolean> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            Enumeration<String> original = super.getHeaderNames();
            while (original != null && original.hasMoreElements()) {
                names.put(original.nextElement(), Boolean.TRUE);
            }
            names.putIfAbsent(CompanionHeaders.TENANT_ID, Boolean.TRUE);
            return Collections.enumeration(names.keySet());
        }
    }
}
