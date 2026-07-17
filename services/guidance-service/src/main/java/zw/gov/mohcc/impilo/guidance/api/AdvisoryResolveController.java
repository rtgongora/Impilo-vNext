package zw.gov.mohcc.impilo.guidance.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.guidance.core.AdvisoryResolveService;
import zw.gov.mohcc.impilo.guidance.core.AdvisoryResolveService.AdvisoryContext;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nompilo Service Advisory read lane (internal). Consumed by the experience-bff
 * ({@code PublicGatewayAdvisoryBffController} for the public gateway lane, and any
 * authenticated shell lane), which resolves trust/context before calling here. Advisories
 * are DATA — this endpoint returns only PUBLISHED, in-window, targeting-matched advisories.
 *
 * <ul>
 *   <li>GET  /internal/v1/guidance/advisory            — resolve the active advisory set for a context</li>
 *   <li>POST /internal/v1/guidance/advisory/impression — record a VIEW/DISMISS/FEEDBACK impression</li>
 *   <li>POST /internal/v1/guidance/advisory/dismiss    — record a dismissal (opaque anon key, no PII)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/guidance/advisory")
public class AdvisoryResolveController {

    private final AdvisoryResolveService service;

    public AdvisoryResolveController(AdvisoryResolveService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> resolve(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @RequestParam(required = false, defaultValue = "public") String audience,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String serviceKey,
            @RequestParam(required = false) String appKey,
            @RequestParam(required = false) String journeyStage) {
        AdvisoryContext ctx = new AdvisoryContext(
                tenantId, audience, deviceType, language, province, district,
                serviceKey, appKey, journeyStage, OffsetDateTime.now());
        List<Map<String, Object>> advisories = service.resolve(ctx);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("audience", ctx.audience());
        data.put("advisories", advisories);
        return ResponseEntity.ok(Map.of("data", data));
    }

    @PostMapping("/impression")
    public ResponseEntity<Map<String, Object>> impression(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID advisoryId = uuid(body.get("advisoryId"));
        if (advisoryId == null) {
            return badRequest("advisoryId is required and must be a valid id.");
        }
        String event = str(body, "event");
        String anonKey = str(body, "anonDismissalKey");
        service.recordImpression(tenantId, advisoryId, event == null ? "VIEW" : event, anonKey);
        return ResponseEntity.ok(Map.of("data", Map.of("recorded", true, "advisoryId", advisoryId.toString())));
    }

    @PostMapping("/dismiss")
    public ResponseEntity<Map<String, Object>> dismiss(
            @RequestHeader(value = "x-tenant-id", required = false) String tenantId,
            @RequestBody Map<String, Object> body) {
        UUID advisoryId = uuid(body.get("advisoryId"));
        if (advisoryId == null) {
            return badRequest("advisoryId is required and must be a valid id.");
        }
        service.recordImpression(tenantId, advisoryId, "DISMISS", str(body, "anonDismissalKey"));
        return ResponseEntity.ok(Map.of("data", Map.of("dismissed", true, "advisoryId", advisoryId.toString())));
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error",
                Map.of("code", "BAD_REQUEST", "message", message)));
    }

    private static UUID uuid(Object v) {
        if (v == null) return null;
        try {
            return UUID.fromString(v.toString().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }
}
