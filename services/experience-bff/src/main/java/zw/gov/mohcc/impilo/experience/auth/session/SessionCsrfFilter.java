package zw.gov.mohcc.impilo.experience.auth.session;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** CSRF protection for cookie-authenticated BFF mutations. Bearer-only API calls are unaffected. */
@Component
public class SessionCsrfFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final OidcSessionService sessions;

    public SessionCsrfFilter(OidcSessionService sessions) { this.sessions = sessions; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String sessionId = SessionBearerTokenResolver.cookie(request, WebAuthSessionStore.SESSION_COOKIE);
        if (sessionId != null && !SAFE.contains(request.getMethod()) &&
                !sessions.csrfMatches(sessionId, request.getHeader("X-CSRF-Token"))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"code\":\"CSRF_REJECTED\",\"message\":\"Session CSRF validation failed\"}}");
            return;
        }
        chain.doFilter(request, response);
    }
}
