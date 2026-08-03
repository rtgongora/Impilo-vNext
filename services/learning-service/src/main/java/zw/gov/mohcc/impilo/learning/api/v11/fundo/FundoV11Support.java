package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.companion.context.RequestContext;

/**
 * Shared helpers for the Phase 5B native Fundo v1.1 controllers. Keeps the
 * envelope/tenant-parsing semantics identical to the Phase 3C / 4A
 * controllers so the v1.1 contract behaviour is uniform across every
 * native Fundo endpoint.
 */
final class FundoV11Support {

    private FundoV11Support() {}

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

    /**
     * Tenant for controllers published on {@code /v1/internal/**}.
     *
     * <p>{@code V11HeaderFilter} populates {@link RequestContext} only for
     * {@code /internal/v1/**} and {@code /external/v1/**} — it self-excludes any other path
     * shape. The Fundo workforce-readiness endpoints are published on {@code /v1/internal/fundo}
     * (the shape in contracts/openapi/learning.openapi.yaml, and what vashandi calls), so the
     * filter never ran for them and {@code RequestContextHolder.require()} threw on EVERY
     * request. Those endpoints have therefore never served a response since they were written.
     *
     * <p>Reads the context when a filter did populate it, and falls back to the trust header on
     * the request when one did not — so the endpoint works on its published path without moving
     * it (which would break the contract) and without weakening anything: absent or unparseable
     * tenant still yields null, and the caller still answers TENANT_REQUIRED.
     */
    static UUID tenantOrNull(jakarta.servlet.http.HttpServletRequest request) {
        RequestContext ctx = zw.gov.mohcc.impilo.companion.context.RequestContextHolder.get();
        if (ctx != null) {
            UUID fromContext = tryParseUuid(ctx.tenantId());
            if (fromContext != null) return fromContext;
        }
        return request == null ? null : tryParseUuid(request.getHeader("X-Tenant-ID"));
    }

    static ResponseEntity<Map<String, Object>> dataEnvelope(Map<String, Object> data) {
        return ResponseEntity.ok(Map.of("data", data));
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
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.status(400).body(Map.of("error", error));
    }
}
