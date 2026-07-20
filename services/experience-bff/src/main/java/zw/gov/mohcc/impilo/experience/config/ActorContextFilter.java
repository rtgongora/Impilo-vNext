package zw.gov.mohcc.impilo.experience.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
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
 * Establishes a <b>server-authoritative</b> actor identity (PII Enforcement Wave 0.3).
 *
 * <p>Previously the browser sent its own {@code X-Actor-ID} = the person's raw Health ID, and the BFF
 * forwarded that client-supplied header downstream ({@code ServiceClientConfig}). That was both a leak
 * ("HID never the browser") and a spoofing hole (a caller could claim any actor). This filter, once
 * Spring Security has validated the bearer JWT, overrides {@code X-Actor-ID} with the {@code health_id}
 * claim from the token — the identity that was cryptographically proven, not the one the client typed.
 * Downstream forwarding then carries the authoritative value.</p>
 *
 * <p>Because the JWT {@code health_id} equals the value the UI currently sends, this override is
 * behaviour-preserving on its own; it is the foundation that lets the browser stop sending and storing
 * the Health ID entirely. Requests with no authenticated JWT (public/anonymous lanes, service-to-service
 * calls that set their own actor context) are left untouched.</p>
 */
public class ActorContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String jwtHealthId = healthIdFromJwt();
        if (jwtHealthId == null || jwtHealthId.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        filterChain.doFilter(new AuthoritativeActorRequest(request, jwtHealthId.trim()), response);
    }

    private static String healthIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = null;
        if (auth instanceof JwtAuthenticationToken token) {
            jwt = token.getToken();
        } else if (auth != null && auth.getPrincipal() instanceof Jwt principal) {
            jwt = principal;
        }
        return jwt == null ? null : jwt.getClaimAsString("health_id");
    }

    /** Read-only request view whose {@code X-Actor-ID} is forced to the JWT-proven Health ID. */
    private static final class AuthoritativeActorRequest extends HttpServletRequestWrapper {

        private final String actorId;

        private AuthoritativeActorRequest(HttpServletRequest request, String actorId) {
            super(request);
            this.actorId = actorId;
        }

        private boolean isActorHeader(String name) {
            return CompanionHeaders.ACTOR_ID.equalsIgnoreCase(name);
        }

        @Override
        public String getHeader(String name) {
            return isActorHeader(name) ? actorId : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return isActorHeader(name)
                    ? Collections.enumeration(Collections.singletonList(actorId))
                    : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            java.util.Map<String, Boolean> names = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            Enumeration<String> original = super.getHeaderNames();
            while (original != null && original.hasMoreElements()) {
                names.put(original.nextElement(), Boolean.TRUE);
            }
            names.putIfAbsent(CompanionHeaders.ACTOR_ID, Boolean.TRUE);
            return Collections.enumeration(names.keySet());
        }
    }
}
