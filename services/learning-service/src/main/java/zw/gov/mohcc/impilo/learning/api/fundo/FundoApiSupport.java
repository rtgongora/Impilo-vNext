package zw.gov.mohcc.impilo.learning.api.fundo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

/** Shared envelope, request-context and type coercion helpers for native Fundo controllers. */
final class FundoApiSupport {

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

    static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.status(status).body(Map.of("error", error));
    }

    static ResponseEntity<Map<String, Object>> invalidTenant() {
        return badRequest("TENANT_INVALID", "X-Tenant-ID is required and must be a UUID");
    }
}
