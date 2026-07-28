package zw.gov.mohcc.impilo.learning.api.fundo;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

/** Shared envelope, request-context and type coercion helpers for native Fundo controllers. */
final class FundoApiSupport {

    private static final Logger log = LoggerFactory.getLogger(FundoApiSupport.class);
    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SYSTEM_ACTOR = "system";

    private FundoApiSupport() {}

    static UUID tryParseUuid(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static UUID requireTenantOrNull(RequestContext ctx) {
        return tryParseUuid(ctx.tenantId());
    }

    static UUID currentTenant() {
        RequestContext ctx = RequestContextHolder.require();
        return requireTenantOrNull(ctx);
    }

    static UUID currentTenantOrDefault() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx == null) {
            return DEFAULT_TENANT;
        }
        UUID tenantId = tryParseUuid(ctx.tenantId());
        return tenantId == null ? DEFAULT_TENANT : tenantId;
    }

    static String currentActorOrSystem() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx == null || ctx.principal() == null || ctx.principal().getName() == null) {
            return SYSTEM_ACTOR;
        }
        return ctx.principal().getName();
    }

    static Set<String> currentRoles() {
        Set<String> roles = new HashSet<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            log.info("FundoApiSupport.currentRoles(): auth is null");
            return roles;
        }
        log.info("FundoApiSupport.currentRoles(): auth type = {}, isAuthenticated = {}, class = {}",
                 auth.getClass().getSimpleName(), auth.isAuthenticated(), auth.getClass().getName());

        // Extract from GrantedAuthority objects
        int grantedCount = 0;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            log.info("  - GrantedAuthority: {}", authority.getAuthority());
            addRole(roles, authority.getAuthority());
            grantedCount++;
        }
        log.info("  Total GrantedAuthorities: {}", grantedCount);

        // Extract from JWT claims
        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            log.info("  Principal is Jwt, subject = {}, issuer = {}", jwt.getSubject(), jwt.getIssuer());
            log.info("  JWT claims keys: {}", jwt.getClaims().keySet());
            addRoleClaims(roles, jwt);
        } else {
            log.info("  Principal is not Jwt: {}", principal == null ? "null" : principal.getClass().getSimpleName());
            if (principal != null) {
                log.info("  Principal details: {}", principal);
            }
        }

        log.info("FundoApiSupport.currentRoles() final result: {}", roles);
        return roles;
    }

    static String currentActorType() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth == null ? null : auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Object actorType = firstClaim(jwt, "actor_type", "actorType", "x_actor_type");
            if (actorType != null && !actorType.toString().isBlank()) {
                return actorType.toString().trim().toUpperCase(Locale.ROOT);
            }
        }
        Set<String> roles = currentRoles();
        if (roles.contains("CITIZEN")) return "CITIZEN";
        if (roles.contains("SYSTEM_ADMIN") || roles.contains("DEVELOPER")) return "SYSTEM";
        return null;
    }

    static boolean hasRole(String role) {
        Set<String> roles = currentRoles();
        String normalizedRole = normalizeRole(role);
        boolean contains = roles.contains(normalizedRole);
        log.info("FundoApiSupport.hasRole('{}') -> normalized='{}', currentRoles={}, contains={}",
                 role, normalizedRole, roles, contains);
        return contains;
    }

    static boolean isSystemAdmin() {
        boolean result = hasRole("SYSTEM_ADMIN");
        log.info("FundoApiSupport.isSystemAdmin() called: result = {}", result);
        return result;
    }

    static boolean isLearnerAllowed() {
        return !isSystemAdmin();
    }

    static ResponseEntity<Map<String, Object>> requireSystemAdmin() {
        return isSystemAdmin() ? null : forbidden("FORBIDDEN", "SYSTEM_ADMIN role is required");
    }

    static ResponseEntity<Map<String, Object>> requireReportAccess() {
        Set<String> roles = currentRoles();
        if (roles.contains("SYSTEM_ADMIN") || roles.contains("FACILITY_ADMIN")) {
            return null;
        }
        return forbidden("FORBIDDEN", "Learning reports require SYSTEM_ADMIN or FACILITY_ADMIN role");
    }

    static ResponseEntity<Map<String, Object>> requireLearnerAccess() {
        return isLearnerAllowed() ? null : forbidden("FORBIDDEN", "SYSTEM_ADMIN users do not participate in learner flows");
    }

    static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Boolean asBoolean(Object value) {
        return value == null ? null : Boolean.parseBoolean(value.toString());
    }

    static ResponseEntity<Map<String, Object>> dataEnvelope(Map<String, Object> data) {
        return ResponseEntity.ok(Map.of("data", data));
    }

    static ResponseEntity<Map<String, Object>> dataEnvelope(Object data) {
        return ResponseEntity.ok(Map.of("data", data == null ? Map.of() : data));
    }

    static ResponseEntity<Map<String, Object>> dataEnvelope(String key, Object value) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put(key, value);
        return ResponseEntity.ok(Map.of("data", data));
    }

    static ResponseEntity<Map<String, Object>> notFound(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.status(404).body(Map.of("error", error));
    }

    static ResponseEntity<Map<String, Object>> conflict(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.status(409).body(Map.of("error", error));
    }

    static ResponseEntity<Map<String, Object>> badRequest(String code, String message) {
        return error(HttpStatus.BAD_REQUEST, code, message);
    }

    static ResponseEntity<Map<String, Object>> forbidden(String code, String message) {
        return error(HttpStatus.FORBIDDEN, code, message);
    }

    static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.status(status).body(Map.of("error", error));
    }

    static ResponseEntity<Map<String, Object>> invalidTenant() {
        return badRequest("TENANT_INVALID", "X-Tenant-ID is required and must be a UUID");
    }

    private static void addRoleClaims(Set<String> roles, Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map) {
            addRoles(roles, map.get("roles"));
        }
        addRoles(roles, jwt.getClaim("roles"));
        addRoles(roles, jwt.getClaim("role"));
        addRoles(roles, jwt.getClaim("authorities"));
    }

    private static void addRoles(Set<String> roles, Object value) {
        if (value instanceof Collection<?> collection) {
            collection.forEach(v -> addRole(roles, v));
        } else if (value instanceof String s) {
            for (String part : s.split(",")) {
                addRole(roles, part);
            }
        }
    }

    private static void addRole(Set<String> roles, Object value) {
        if (value == null) return;
        String role = normalizeRole(value.toString());
        if (!role.isBlank()) roles.add(role);
    }

    private static String normalizeRole(String role) {
        if (role == null) return "";
        String out = role.trim().toUpperCase(Locale.ROOT);
        if (out.startsWith("ROLE_")) out = out.substring(5);
        if (out.startsWith("SCOPE_")) out = out.substring(6);
        return out;
    }

    private static Object firstClaim(Jwt jwt, String... names) {
        for (String name : names) {
            Object value = jwt.getClaim(name);
            if (value != null) return value;
        }
        return null;
    }
}
