package zw.gov.mohcc.impilo.experience.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.*;

/**
 * Backend role-based access control interceptor for sensitive BFF endpoints.
 *
 * <p>Extracts Keycloak realm roles from the JWT Authorization header and
 * enforces role requirements per path pattern. Returns 403 Forbidden if
 * the caller lacks the required role(s).</p>
 *
 * <p>Path → role mappings use the same role-group concept as the frontend
 * AuthGuardProvider: abstract group names resolve to concrete Keycloak roles.</p>
 */
@Component
public class RoleGuardInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RoleGuardInterceptor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Role groups: abstract names → concrete Keycloak realm roles.
     * Must stay aligned with AuthGuardProvider.tsx ROLE_GROUPS.
     */
    private static final Map<String, Set<String>> ROLE_GROUPS = Map.of(
            "ADMIN", Set.of("SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"),
            "FINANCE", Set.of("SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE")
    );

    /**
     * Path prefixes that require specific role groups.
     * Order matters: more specific paths should come first.
     */
    private static final List<PathPolicy> POLICIES = List.of(
            new PathPolicy("/internal/v1/admin/", "ADMIN"),
            new PathPolicy("/internal/v1/finance/", "FINANCE")
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws IOException {
        String path = request.getRequestURI();

        // Find applicable policy
        PathPolicy policy = null;
        for (PathPolicy p : POLICIES) {
            if (path.startsWith(p.pathPrefix)) {
                policy = p;
                break;
            }
        }
        if (policy == null) return true; // No policy → permit

        // Extract roles from JWT
        Set<String> callerRoles = extractRolesFromToken(request);
        if (callerRoles.isEmpty()) {
            log.warn("Access denied: no valid roles in token for path={}", path);
            sendForbidden(response, "Authentication required");
            return false;
        }

        // Check role group
        Set<String> allowedRoles = ROLE_GROUPS.get(policy.requiredGroup);
        if (allowedRoles == null) {
            log.error("Unknown role group: {}", policy.requiredGroup);
            sendForbidden(response, "Configuration error");
            return false;
        }

        boolean hasRole = callerRoles.stream().anyMatch(allowedRoles::contains);
        if (!hasRole) {
            log.warn("Access denied: path={}, callerRoles={}, requiredGroup={}",
                    path, callerRoles, policy.requiredGroup);
            sendForbidden(response, "Insufficient privileges. Required: " + policy.requiredGroup);
            return false;
        }

        return true;
    }

    /**
     * Extract Keycloak realm roles from the JWT in the Authorization header.
     * Uses base64 decoding of the JWT payload (same approach as AuthSessionController).
     */
    private Set<String> extractRolesFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Set.of();
        }

        String token = authHeader.substring(7);
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return Set.of();

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = MAPPER.readTree(payloadJson);

            Set<String> roles = new HashSet<>();
            if (claims.has("realm_access") && claims.get("realm_access").has("roles")) {
                for (JsonNode role : claims.get("realm_access").get("roles")) {
                    String r = role.asText();
                    if (!r.startsWith("default-roles-") && !r.equals("offline_access")
                            && !r.equals("uma_authorization")) {
                        roles.add(r);
                    }
                }
            }
            return roles;
        } catch (Exception e) {
            log.warn("Failed to extract roles from JWT: {}", e.getMessage());
            return Set.of();
        }
    }

    private void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"" + message + "\"}}");
    }

    private record PathPolicy(String pathPrefix, String requiredGroup) {}
}
