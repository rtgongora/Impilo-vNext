package zw.gov.mohcc.impilo.guidance.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.guidance.core.ContextGuidanceService;
import zw.gov.mohcc.impilo.guidance.core.ContextGuidanceService.GuidanceContext;

import java.util.*;

/**
 * Nompilo context-aware guidance API (internal). Consumed by the Experience BFF
 * {@code NompiloGuidanceController}, which resolves trust/policy and the real service signals
 * before calling here. The UI never calls this directly.
 *
 * <ul>
 *   <li>POST /context        — resolve the contextual guidance set for a route/user-type/trust + signals</li>
 *   <li>POST /explain-locked — map a denied/locked reason code to a safe plain-language explanation</li>
 *   <li>POST /dismiss        — record a person's dismissal of a guidance item</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/guidance/nompilo")
public class ContextGuidanceController {

    private final ContextGuidanceService service;

    public ContextGuidanceController(ContextGuidanceService service) {
        this.service = service;
    }

    @PostMapping("/context")
    public ResponseEntity<Map<String, Object>> context(
            @RequestHeader("x-tenant-id") String tenantId,
            @RequestHeader(value = "x-actor-id", required = false) String actorId,
            @RequestBody(required = false) Map<String, Object> body) {
        GuidanceContext ctx = toContext(tenantId, actorId, body);
        List<Map<String, Object>> items = service.resolve(ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("context", Map.of(
                "userType", ctx.userType(),
                "trustLoa", ctx.trustLoa(),
                "routePath", ctx.routePath(),
                "relationship", ctx.relationship()));
        data.put("guidance", items);
        return ResponseEntity.ok(Map.of("data", data));
    }

    @PostMapping("/explain-locked")
    public ResponseEntity<Map<String, Object>> explainLocked(
            @RequestHeader("x-tenant-id") String tenantId,
            @RequestHeader(value = "x-actor-id", required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        String reasonCode = str(body, "reasonCode");
        GuidanceContext ctx = toContext(tenantId, actorId, body);
        return ResponseEntity.ok(Map.of("data", service.explainLocked(tenantId, reasonCode, ctx)));
    }

    @PostMapping("/dismiss")
    public ResponseEntity<Map<String, Object>> dismiss(
            @RequestHeader("x-tenant-id") String tenantId,
            @RequestHeader(value = "x-actor-id", required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        String key = str(body, "guidanceKey");
        if (actorId == null || key == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    Map.of("code", "BAD_REQUEST", "message", "actorId and guidanceKey are required")));
        }
        service.dismiss(tenantId, actorId, key);
        return ResponseEntity.ok(Map.of("data", Map.of("dismissed", true, "guidanceKey", key)));
    }

    @SuppressWarnings("unchecked")
    private GuidanceContext toContext(String tenantId, String actorId, Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Set<String> signals = new HashSet<>();
        Object raw = b.get("signals");
        if (raw instanceof Collection<?> c) {
            for (Object o : c) {
                if (o != null) signals.add(String.valueOf(o));
            }
        }
        return new GuidanceContext(
                tenantId,
                actorId,
                str(b, "userType"),
                str(b, "activeRole"),
                intOf(b.get("trustLoa"), 1),
                str(b, "routePath"),
                str(b, "relationship"),
                signals);
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static int intOf(Object v, int fallback) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }
}
