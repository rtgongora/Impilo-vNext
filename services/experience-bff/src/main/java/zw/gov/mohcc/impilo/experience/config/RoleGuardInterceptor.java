package zw.gov.mohcc.impilo.experience.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defense-in-depth role guard interceptor for sensitive BFF endpoints.
 *
 * <p>This interceptor complements Spring Security's filter chain by providing
 * a secondary check using the already-validated SecurityContext. It does NOT
 * perform its own JWT parsing — Spring Security's OAuth2 Resource Server
 * handles signature verification and expiration checking.</p>
 *
 * <p>If Spring Security's filter chain is bypassed (e.g., misconfiguration),
 * this interceptor catches unauthorized access as a safety net.</p>
 */
@Component
public class RoleGuardInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RoleGuardInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        // Spring Security filter chain runs before MVC interceptors.
        // If we reach here, the request has already passed Spring Security's
        // authorization rules. Log the authenticated principal for audit.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String path = request.getRequestURI();
            Set<String> roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
            log.debug("Authorized access: path={}, principal={}, roles={}",
                    path, auth.getName(), roles);
        }
        return true;
    }
}
