package zw.gov.mohcc.impilo.experience.auth.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Route restriction for constrained recovery sessions (Tshepo trust doctrine).
 *
 * <p>A browser session established via recovery codes may only reach the account-recovery
 * surface: OIDC session/logout/enrollment routes and the account-security settings API
 * (factor inspection, credential removal, session termination). Every other cookie-backed
 * request is refused with the canonical {@code RECOVERY_REQUIRED} decision so the shell can
 * route the user into factor re-enrollment. Clinical, regulatory, administrative, financial,
 * marketplace-work and platform operations are therefore denied by construction.</p>
 */
@Component
public class RecoverySessionFilter extends OncePerRequestFilter {
    private static final Logger RECOVERY_AUDIT =
            LoggerFactory.getLogger("impilo.trust.recovery.audit");

    /** Paths a constrained recovery session may reach. Prefix match on the request path. */
    static final List<String> PERMITTED_PREFIXES = List.of(
            "/internal/v1/auth/oidc/",
            "/internal/v1/auth/logout",
            "/internal/v1/settings/security");

    private final OidcSessionService sessions;

    public RecoverySessionFilter(OidcSessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String sessionId = SessionBearerTokenResolver.cookie(request, WebAuthSessionStore.SESSION_COOKIE);
        if (sessionId == null || isPermitted(request.getRequestURI())) {
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

    static boolean isPermitted(String path) {
        if (path == null) return false;
        for (String prefix : PERMITTED_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }
}
