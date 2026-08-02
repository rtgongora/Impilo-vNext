package zw.gov.mohcc.impilo.experience.auth.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Route restriction for constrained recovery sessions (Tshepo trust doctrine, CP3 closure).
 *
 * <p>The decision is expressed in canonical {@code ACTION:RESOURCE_TYPE} terms, mirroring the
 * Tshepo authz {@code recoveryPermittedActions} allowlist — the route prefix is only how an
 * HTTP request is mapped onto its canonical operation, never the authorization boundary
 * itself. Requests that map to no known recovery operation are DENIED (fail closed), so a
 * future controller added under a "permitted" prefix is not silently reachable.</p>
 *
 * <p>Permitted during CONSTRAINED_RECOVERY: sanitized factor/status inspection, initiating
 * enrollment of a replacement approved factor, starting a fresh ordinary authentication,
 * terminating sessions, and logout. Denied by construction: credential deletion (could remove
 * the last valid factor), recovery-code regeneration, password/email changes, administrative
 * recovery cases, and every clinical/regulatory/administrative/financial/marketplace/platform
 * operation.</p>
 */
@Component
public class RecoverySessionFilter extends OncePerRequestFilter {
    private static final Logger RECOVERY_AUDIT =
            LoggerFactory.getLogger("impilo.trust.recovery.audit");

    /** Canonical operation a request maps to; {@code null} pattern groups are literal paths. */
    record RecoveryOperation(String method, Pattern path, String action, String resourceType) {
        boolean matches(String requestMethod, String requestPath) {
            return (method == null || method.equalsIgnoreCase(requestMethod))
                    && path.matcher(requestPath).matches();
        }
        String canonical() { return action + ":" + resourceType; }
    }

    /**
     * HTTP surface → canonical operation registry. Every request a recovery session may need
     * is enumerated here explicitly; anything not listed is refused.
     */
    static final List<RecoveryOperation> OPERATIONS = List.of(
            // OIDC session flow: begin/complete/inspect a fresh ordinary authentication.
            new RecoveryOperation("GET", Pattern.compile("/internal/v1/auth/oidc/authorize"), "CREATE", "AUTH_SESSION"),
            new RecoveryOperation("GET", Pattern.compile("/internal/v1/auth/oidc/callback"), "CREATE", "AUTH_SESSION"),
            new RecoveryOperation("GET", Pattern.compile("/internal/v1/auth/oidc/session"), "READ", "AUTH_SESSION"),
            new RecoveryOperation("POST", Pattern.compile("/internal/v1/auth/oidc/step-up"), "CREATE", "AUTH_SESSION"),
            // Enrollment of a replacement approved factor (action-name restriction is enforced
            // in the controller, which can read the body; see OidcSessionController#action).
            new RecoveryOperation("POST", Pattern.compile("/internal/v1/auth/oidc/action"), "CREATE", "AUTH_FACTOR"),
            new RecoveryOperation("POST", Pattern.compile("/internal/v1/auth/oidc/logout"), "LOGOUT", "*"),
            new RecoveryOperation("POST", Pattern.compile("/internal/v1/auth/logout"), "LOGOUT", "*"),
            // Account security: sanitized factor/session inspection.
            new RecoveryOperation("GET", Pattern.compile("/internal/v1/settings/security/?"), "READ", "ACCOUNT_SECURITY"),
            // Session termination (access-reducing; policy allows it during recovery).
            new RecoveryOperation("DELETE", Pattern.compile("/internal/v1/settings/security/sessions/[^/]+"), "DELETE", "AUTH_SESSION"),
            new RecoveryOperation("POST", Pattern.compile("/internal/v1/settings/security/sessions/logout-others"), "DELETE", "AUTH_SESSION"),
            // Credential deletion maps to DELETE:AUTH_FACTOR — deliberately NOT in the
            // allowlist below, so it is refused during recovery (last-factor protection).
            new RecoveryOperation("DELETE", Pattern.compile("/internal/v1/settings/security/credentials/[^/]+"), "DELETE", "AUTH_FACTOR"),
            // Administrative lost-device recovery cases: never reachable from a recovery session.
            new RecoveryOperation(null, Pattern.compile("/internal/v1/settings/security/recovery-cases(/.*)?"), "EXECUTE", "ADMIN_RECOVERY"));

    /**
     * Canonical allowlist — mirror of tshepo-authz {@code AuthzProperties#recoveryPermittedActions}.
     * The two lists must stay identical so BFF edge enforcement and policy decisions agree.
     */
    static final Set<String> PERMITTED_CANONICAL = Set.of(
            "LOGOUT:*",
            "READ:ACCOUNT_SECURITY",
            "READ:AUTH_FACTOR",
            "CREATE:AUTH_FACTOR",
            "CREATE:AUTH_SESSION",
            "READ:AUTH_SESSION",
            "DELETE:AUTH_SESSION");

    private final OidcSessionService sessions;

    public RecoverySessionFilter(OidcSessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String sessionId = SessionBearerTokenResolver.cookie(request, WebAuthSessionStore.SESSION_COOKIE);
        if (sessionId == null) {
            chain.doFilter(request, response);
            return;
        }
        boolean recovery = sessions.session(sessionId)
                .map(WebAuthSessionStore.SessionData::recovery)
                .orElse(false);
        if (!recovery) {
            chain.doFilter(request, response);
            return;
        }
        if (isPermitted(request.getMethod(), request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        RECOVERY_AUDIT.info("event=recovery_route_denied path={} method={}",
                request.getRequestURI(), request.getMethod());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":{\"code\":\"RECOVERY_REQUIRED\","
                        + "\"decision\":\"RECOVERY_REQUIRED\","
                        + "\"reason_code\":\"CONSTRAINED_RECOVERY_SESSION\","
                        + "\"user_message_key\":\"trust.recovery.enroll_required\","
                        + "\"required_action\":\"ENROLL_REPLACEMENT_FACTOR\"}}");
    }

    /** Canonical decision: map the request to its operation, then consult the allowlist. */
    static boolean isPermitted(String method, String path) {
        if (path == null) return false;
        for (RecoveryOperation operation : OPERATIONS) {
            if (operation.matches(method, path)) {
                return PERMITTED_CANONICAL.contains(operation.canonical());
            }
        }
        return false;
    }
}
