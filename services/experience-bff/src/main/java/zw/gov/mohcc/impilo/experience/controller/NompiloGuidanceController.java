package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.LearningServiceClient;
import zw.gov.mohcc.impilo.experience.service.NompiloSignalService;
import zw.gov.mohcc.impilo.experience.service.NompiloSignalService.CitizenSignals;
import zw.gov.mohcc.impilo.experience.service.PiiAccessAuditService;

import java.util.*;

/**
 * Nompilo Intelligent Layer BFF — the on-platform guide.
 *
 * <p>Nompilo guides, explains, and routes to the sovereign owners; it owns no transaction. This
 * controller resolves the caller's context (route, user-type, trust, relationship), computes the
 * real service <em>signals</em> via {@link NompiloSignalService}, and asks the Nompilo SoR
 * (guidance-service) for the contextual guidance set / locked-state explanation. Khuluma follow-ups
 * go through the real guidance-service handoff lifecycle ({@code core.nompilo.handoff.requested}) —
 * Nompilo never sends notifications itself. Fundo content is surfaced via the learning-service —
 * Nompilo recommends, it does not author.</p>
 *
 * <p>Sensitive guidance (locked/denied explanations, cross-person/proxy, provider work context) is
 * audited via {@link PiiAccessAuditService}. We never expose the protected data in guidance text.</p>
 *
 * <ul>
 *   <li>POST /context        — contextual guidance set for the current route + signals</li>
 *   <li>GET  /next-actions   — ranked next-best-actions for the citizen (signals auto-derived)</li>
 *   <li>POST /explain-locked — safe plain-language explanation of a denied/locked reason code</li>
 *   <li>POST /dismiss        — record a person's dismissal of a guidance item</li>
 *   <li>POST /follow-up      — request a Khuluma follow-up via the handoff lifecycle</li>
 *   <li>GET  /fundo/{id}     — surface a recommended Fundo content reference</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/nompilo")
public class NompiloGuidanceController {

    private static final Logger log = LoggerFactory.getLogger(NompiloGuidanceController.class);

    private final GuidanceServiceClient guidanceClient;
    private final NompiloSignalService signalService;
    private final LearningServiceClient learningClient;
    private final PiiAccessAuditService auditService;

    public NompiloGuidanceController(GuidanceServiceClient guidanceClient,
                                     NompiloSignalService signalService,
                                     LearningServiceClient learningClient,
                                     PiiAccessAuditService auditService) {
        this.guidanceClient = guidanceClient;
        this.signalService = signalService;
        this.learningClient = learningClient;
        this.auditService = auditService;
    }

    /** Contextual guidance for the current route. Citizen signals are derived; other contexts pass theirs. */
    @PostMapping("/context")
    public ResponseEntity<Map<String, Object>> context(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Subject-ID", required = false) String subjectId,
            @RequestHeader(value = "X-Facility-ID", required = false) String facilityId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        try {
            Map<String, Object> b = body == null ? Map.of() : body;
            String userType = userType(actorType);
            String routePath = str(b, "routePath");
            boolean delegated = subjectId != null && !subjectId.isBlank() && !subjectId.equals(actorId);
            String relationship = delegated ? "PROXY" : str(b, "relationship");

            Map<String, Object> req = new LinkedHashMap<>();
            req.put("userType", userType);
            req.put("routePath", routePath);
            req.put("relationship", relationship);
            req.put("activeRole", str(b, "activeRole"));

            Set<String> signals = new LinkedHashSet<>(stringList(b.get("signals")));
            int trustLoa = intOf(b.get("trustLoa"), 1);

            // For a citizen viewing their own wallet/home, derive the real signals from service state.
            if ("CITIZEN".equals(userType) && !delegated) {
                CitizenSignals derived = signalService.citizenSignals(actorId, signals.contains("first_time"));
                signals.addAll(derived.signals());
                trustLoa = Math.max(trustLoa, derived.trustLoa());
            }
            req.put("signals", new ArrayList<>(signals));
            req.put("trustLoa", trustLoa);

            JsonNode result = guidanceClient.resolveContext(req);

            // Audit cross-person/proxy guidance, and any item flagged audit-required (sensitive).
            if (delegated) {
                audit(tenantId, actorId, actorType, subjectId, "NOMPILO_GUIDANCE_PROXY", routePath, request, facilityId);
            }
            auditFlaggedItems(result, tenantId, actorId, actorType, delegated ? subjectId : actorId, routePath, request, facilityId);

            return ok(result, requestId, correlationId);
        } catch (Exception e) {
            log.error("Nompilo context failed: {}", e.getMessage());
            return partial(requestId, correlationId);
        }
    }

    /** Ranked next-best-actions for the signed-in citizen (signals auto-derived). */
    @GetMapping("/next-actions")
    public ResponseEntity<Map<String, Object>> nextActions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestParam(value = "routePath", defaultValue = "/citizen/wallet") String routePath) {
        try {
            CitizenSignals derived = signalService.citizenSignals(actorId, false);
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("userType", userType(actorType));
            req.put("routePath", routePath);
            req.put("relationship", "SELF");
            req.put("signals", new ArrayList<>(derived.signals()));
            req.put("trustLoa", derived.trustLoa());
            JsonNode result = guidanceClient.resolveContext(req);
            return ok(result, requestId, correlationId);
        } catch (Exception e) {
            log.error("Nompilo next-actions failed: {}", e.getMessage());
            return partial(requestId, correlationId);
        }
    }

    /** Safe plain-language explanation of a denied/locked action. Audited (sensitive). */
    @PostMapping("/explain-locked")
    public ResponseEntity<Map<String, Object>> explainLocked(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Subject-ID", required = false) String subjectId,
            @RequestHeader(value = "X-Facility-ID", required = false) String facilityId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        try {
            Map<String, Object> req = new LinkedHashMap<>(body);
            req.putIfAbsent("userType", userType(actorType));
            JsonNode result = guidanceClient.explainLocked(req);
            // A denied-action explanation is always recorded — who asked, about what, on which route.
            boolean delegated = subjectId != null && !subjectId.isBlank() && !subjectId.equals(actorId);
            audit(tenantId, actorId, actorType, delegated ? subjectId : actorId,
                    "NOMPILO_LOCKED_EXPLANATION", str(body, "routePath"), request, facilityId);
            return ok(result, requestId, correlationId);
        } catch (Exception e) {
            log.error("Nompilo explain-locked failed: {}", e.getMessage());
            return partial(requestId, correlationId);
        }
    }

    /** Record a person's dismissal of a guidance item (display state only). */
    @PostMapping("/dismiss")
    public ResponseEntity<Map<String, Object>> dismiss(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("actorId", actorId);
            req.put("guidanceKey", str(body, "guidanceKey"));
            JsonNode result = guidanceClient.dismiss(req);
            return ok(result, requestId, correlationId);
        } catch (Exception e) {
            log.error("Nompilo dismiss failed: {}", e.getMessage());
            return partial(requestId, correlationId);
        }
    }

    /**
     * Request a Khuluma follow-up. Nompilo REQUESTS comms; it never sends. We route through the real
     * guidance-service handoff lifecycle, which emits {@code core.nompilo.handoff.requested} that the
     * comms (Khuluma) and Rito subscribers consume.
     */
    @PostMapping("/follow-up")
    public ResponseEntity<Map<String, Object>> requestFollowUp(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(value = "X-Subject-ID", required = false) String subjectId,
            @RequestHeader(value = "X-Facility-ID", required = false) String facilityId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("actorId", actorId);
            req.put("subjectId", subjectId != null ? subjectId : actorId);
            req.put("reason", str(body, "reason"));
            req.put("priority", body.getOrDefault("priority", "MEDIUM"));
            req.put("destination", body.getOrDefault("destination", "CALL_CENTRE"));
            req.put("flowId", str(body, "guidanceKey"));
            req.put("context", body.get("context"));
            JsonNode result = guidanceClient.requestHandoff(req);
            audit(tenantId, actorId, "CITIZEN", subjectId != null ? subjectId : actorId,
                    "NOMPILO_KHULUMA_FOLLOW_UP", str(body, "routePath"), request, facilityId);
            return ok(result, requestId, correlationId);
        } catch (Exception e) {
            log.error("Nompilo follow-up request failed: {}", e.getMessage());
            return partial(requestId, correlationId);
        }
    }

    /** Surface a recommended Fundo content reference (recommend/route; never duplicate content). */
    @GetMapping("/fundo/{resourceId}")
    public ResponseEntity<Map<String, Object>> fundoContent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String resourceId) {
        try {
            JsonNode resource = learningClient.getResource(resourceId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("_source", "learning-service (Impilo Fundo)");
            data.put("resourceId", resourceId);
            data.put("resource", resource);
            data.put("openRoute", "/learning/catalog");
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Nompilo fundo content failed: {}", e.getMessage());
            return partial(requestId, correlationId);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────

    private void auditFlaggedItems(JsonNode result, String tenantId, String actorId, String actorType,
                                   String subjectId, String routePath, HttpServletRequest request, String facilityId) {
        if (result == null) return;
        JsonNode items = result.get("guidance");
        if (items == null || !items.isArray()) return;
        boolean anySensitive = false;
        for (JsonNode it : items) {
            if (it.path("auditRequired").asBoolean(false)) {
                anySensitive = true;
                break;
            }
        }
        if (anySensitive) {
            audit(tenantId, actorId, actorType, subjectId, "NOMPILO_SENSITIVE_GUIDANCE", routePath, request, facilityId);
        }
    }

    private void audit(String tenantId, String actorId, String actorType, String subjectId,
                       String action, String routePath, HttpServletRequest request, String facilityId) {
        auditService.recordAccess(
                tenantId,
                actorId,
                actorType != null ? actorType : "CITIZEN",
                subjectId,
                "PATIENT",
                action,
                new String[]{routePath != null ? routePath : "guidance"},
                "GUIDANCE",
                channel(request),
                "MINIMAL",
                request != null ? request.getRemoteAddr() : null,
                request != null ? request.getHeader("User-Agent") : null,
                facilityId);
    }

    private String channel(HttpServletRequest request) {
        if (request == null) return "WEB";
        String src = request.getHeader("X-Request-Source");
        return src != null && src.toLowerCase().contains("mobile") ? "APP" : "WEB";
    }

    private String userType(String actorType) {
        if (actorType == null) return "CITIZEN";
        return switch (actorType.toUpperCase()) {
            case "PROVIDER" -> "PROVIDER";
            case "CAREGIVER", "GUARDIAN" -> "GUARDIAN";
            case "OPERATOR", "FACILITY_ADMIN" -> "FACILITY_ADMIN";
            case "SUPPORT" -> "SUPPORT";
            default -> "CITIZEN";
        };
    }

    private ResponseEntity<Map<String, Object>> ok(JsonNode result, String requestId, String correlationId) {
        return ResponseEntity.ok(Map.of(
                "data", result != null ? result : Map.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> partial(String requestId, String correlationId) {
        // Safe partial: never fail the page. Return an empty guidance set with a truthful note.
        return ResponseEntity.ok(Map.of(
                "data", Map.of("guidance", List.of(), "degraded", true),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object v) {
        if (v instanceof Collection<?> c) {
            List<String> out = new ArrayList<>();
            for (Object o : c) if (o != null) out.add(String.valueOf(o));
            return out;
        }
        return List.of();
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
