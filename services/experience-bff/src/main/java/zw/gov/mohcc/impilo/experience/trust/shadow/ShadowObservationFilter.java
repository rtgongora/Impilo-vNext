package zw.gov.mohcc.impilo.experience.trust.shadow;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Observes what authorization <em>would</em> decide, and changes nothing about what it did.
 *
 * <h2>The measurement this exists for</h2>
 * <p>The browser holds an opaque BFF session cookie and sends no {@code Authorization} header, so
 * ext_authz validates no session and resolves no roles — while 489 of 525 active ALLOW rules (93%)
 * require one. The BFF, by contrast, <em>does</em> hold the user's access token: {@code
 * SessionBearerTokenResolver} exchanges the cookie for it on every request and Spring Security
 * validates it. This filter reports what the PDP would have concluded had it seen the same token.
 * It does not attach it to anything.</p>
 *
 * <h2>Why observation here is safe</h2>
 * <p><b>Isolation is physical, not conditional.</b> The production decision was made by ext_authz
 * at Envoy before this filter's class was loaded for this request; by the time anything here runs,
 * the verdict is final and upstream. There is no ordering in which a shadow answer could replace
 * it — a stronger guarantee than any flag, and the reason this shape was chosen over attaching a
 * bearer to the live path. (Commit {@code 78d217bb9} reverted that alternative: the BFF calls
 * domain services by direct cluster DNS, so a bearer attached outbound would have reached ~100
 * services without traversing Envoy at all. It must stay reverted.)</p>
 *
 * <h2>What is captured, and when</h2>
 * <p>Everything is read on the request thread and packed into an immutable
 * {@link ShadowRequestEnvelope} <b>before</b> any async work exists. Nothing servlet-typed crosses
 * that boundary — see the envelope's class doc for the recycled-request failure this prevents.</p>
 *
 * <h2>What this filter can observe, and what it structurally cannot</h2>
 * <p>Only ALLOWED traffic reaches the BFF. A DENY is returned by Envoy and never arrives here, so
 * {@code DENY_TO_PERMIT} deltas are <b>invisible from this vantage point</b>. The dataset therefore
 * answers "what would break" and cannot answer "what would newly be permitted". That is a property
 * of where the observer sits, not an omission, and it belongs in the evidence report.</p>
 */
public class ShadowObservationFilter extends OncePerRequestFilter {

    private final ShadowObservationProperties properties;
    private final ShadowProbeDispatcher dispatcher;

    public ShadowObservationFilter(ShadowObservationProperties properties,
                                   ShadowProbeDispatcher dispatcher) {
        this.properties = properties;
        this.dispatcher = dispatcher;
    }

    /**
     * Routes Envoy exempts from ext_authz, so there is no production verdict to compare against —
     * and, for the auth lane, no session yet to shadow. Mirrors the bypass list in
     * {@code deploy/helm/impilo-vnext/templates/envoy.yaml}; if that list changes, this must.
     */
    private static final List<String> NOT_GATED_BY_EXT_AUTHZ = List.of(
            "/actuator", "/internal/v1/auth/", "/internal/v1/public/");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!properties.dispatchable() || notGated(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        // Capture BEFORE the chain runs. Downstream code may consume the session, and the container
        // may recycle the request the instant the response commits; both make a post-chain read of
        // the request unreliable in ways that do not throw.
        ShadowRequestEnvelope envelope = capture(request);

        filterChain.doFilter(request, response);

        if (envelope == null) {
            return;
        }
        dispatcher.markEligible();

        // The canary is checked first and is exclusive: a request measured both ways would count
        // its own probe cost twice and put two rows in the log for one decision.
        if (roll(properties.getCanarySampleRate())) {
            dispatcher.runInlineCanary(envelope);
        } else if (roll(properties.getAsyncSampleRate())) {
            dispatcher.dispatchAsync(envelope);
        }
    }

    private static boolean notGated(String uri) {
        if (uri == null) return true;
        for (String prefix : NOT_GATED_BY_EXT_AUTHZ) {
            if (uri.startsWith(prefix)) return true;
        }
        return false;
    }

    private static boolean roll(double rate) {
        if (rate <= 0) return false;
        if (rate >= 1) return true;
        return ThreadLocalRandom.current().nextDouble() < rate;
    }

    /**
     * Build the envelope, or return null when there is nothing to measure.
     *
     * <p>The bearer is taken from the <b>already-validated</b> {@link JwtAuthenticationToken} rather
     * than re-resolved from the session store. That is deliberate twice over: it costs no extra
     * Redis round trip on the request path, and it guarantees the token being shadowed is precisely
     * the one that authenticated this request rather than whatever the store holds a moment later.
     * If Spring Security did not authenticate a JWT, there is no session to shadow and no probe is
     * sent — an unauthenticated request has no roles to reveal.</p>
     */
    private ShadowRequestEnvelope capture(HttpServletRequest request) {
        String bearer = validatedBearer();
        if (bearer == null || bearer.isBlank()) {
            dispatcher.markSkippedNoBearer();
            return null;
        }
        String prodVerdict = request.getHeader(TrustHeaders.DECISION);
        if (prodVerdict == null || prodVerdict.isBlank()) {
            // No x-decision means ext_authz did not run on this route. Recording it against a null
            // baseline would put rows in the dataset that cannot express a verdict delta and would
            // dilute exactly the ratio the report is built on.
            dispatcher.markSkippedNoVerdict();
            return null;
        }

        Map<String, String> ctx = new LinkedHashMap<>();
        // PDP-authoritative on a gated route: Envoy's allowed_upstream_headers overwrites the
        // client's copy with what PolicyEngine emitted.
        put(ctx, request, "actorId", TrustHeaders.ACTOR_ID);
        put(ctx, request, "tenantId", TrustHeaders.TENANT_ID);
        put(ctx, request, "correlationId", TrustHeaders.CORRELATION_ID);
        put(ctx, request, "providerId", TrustHeaders.PROVIDER_ID);
        put(ctx, request, "subjectId", TrustHeaders.SUBJECT_ID);
        put(ctx, request, "assuranceLevel", TrustHeaders.ASSURANCE_LEVEL);
        // Still client-supplied — a retained gap recorded in envoy.yaml, not one this observer
        // closes. Passed through unchanged so shadow and production see the same values.
        put(ctx, request, "purposeOfUse", TrustHeaders.PURPOSE_OF_USE);
        put(ctx, request, "facilityId", TrustHeaders.FACILITY_ID);
        put(ctx, request, "workspaceId", TrustHeaders.WORKSPACE_ID);
        put(ctx, request, "departmentId", TrustHeaders.DEPARTMENT_ID);
        put(ctx, request, "wardId", TrustHeaders.WARD_ID);
        put(ctx, request, "programmeId", TrustHeaders.PROGRAMME_ID);
        put(ctx, request, "shiftId", TrustHeaders.SHIFT_ID);
        put(ctx, request, "deviceFingerprint", TrustHeaders.DEVICE_FINGERPRINT);
        put(ctx, request, "escalationGrantId", TrustHeaders.ESCALATION_GRANT_ID);
        put(ctx, request, "workflowState", TrustHeaders.WORKFLOW_STATE);
        // The duty token is a credential that authenticates itself by introspection. It is carried
        // so the PDP can prove the work mode the same way it does in production; the BFF asserts
        // nothing about it and never decodes it.
        put(ctx, request, "workContextToken", TrustHeaders.WORK_CONTEXT_TOKEN);

        return new ShadowRequestEnvelope(
                request.getMethod(),
                request.getRequestURI(),
                prodVerdict,
                request.getHeader(TrustHeaders.ACTOR_TYPE),
                ctx,
                bearer,
                System.nanoTime());
    }

    private static void put(Map<String, String> ctx, HttpServletRequest request,
                            String key, String header) {
        String value = request.getHeader(header);
        // Absent stays absent. Map.copyOf rejects nulls, and "" is a value PolicyEngine would treat
        // differently from a missing header.
        if (value != null && !value.isBlank()) {
            ctx.put(key, value);
        }
    }

    private static String validatedBearer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return token.getToken().getTokenValue();
        }
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getTokenValue();
        }
        return null;
    }
}
