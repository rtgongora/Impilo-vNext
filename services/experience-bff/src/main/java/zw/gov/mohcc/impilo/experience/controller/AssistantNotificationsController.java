package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.intelligence.HealthIntelligenceService;
import zw.gov.mohcc.impilo.experience.service.NompiloSignalService;
import zw.gov.mohcc.impilo.experience.service.NompiloSignalService.CitizenSignals;
import zw.gov.mohcc.impilo.experience.service.PiiAccessAuditService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Proactive assistant notifications endpoint.
 *
 * <p>Surfaces contextual, work-mode-aware notifications for the ProactiveAssistant
 * UI component. Aggregates signals from guidance, clinical alerts, operational nudges,
 * and wellness prompts. Returns an empty list when no contextual signals are available
 * — the assistant is always non-blocking.</p>
 *
 * <p>Future: wire to guidance-service and clinical-knowledge-platform-service for
 * real-time signal aggregation based on work_mode, facility, and shift context.</p>
 *
 * <p>The conversational surface ({@code POST /chat}) routes the shell's ProactiveAssistant
 * through the REAL guidance / LLM path (guidance-service {@code /ask} → llm-orchestration
 * {@code /llm/structured}) with the honesty gate intact. Nompilo guides; it owns no
 * transaction and NEVER fabricates success, availability, or facts. When the guidance path
 * is unavailable or no real provider answered, the reply is honest and {@code degraded=true};
 * we never invent an answer. When a failed citizen action supplies a {@code failure} context,
 * the reply is biased toward recovery via the guidance {@code explain-locked} path.</p>
 */
@RestController
@RequestMapping("/internal/v1/assistant")
public class AssistantNotificationsController {

    private static final Logger log = LoggerFactory.getLogger(AssistantNotificationsController.class);

    /** Nompilo never claims a transaction succeeded and never overrides professional judgement. */
    private static final String NOMPILO_DISCLAIMER =
            "Nompilo offers general guidance and directions. It never confirms a transaction succeeded "
                    + "and never overrides professional medical judgement — always confirm actions in the "
                    + "relevant service.";

    private final HealthIntelligenceService healthIntelligenceService;
    private final GuidanceServiceClient guidanceClient;
    private final NompiloSignalService signalService;
    private final PiiAccessAuditService auditService;

    public AssistantNotificationsController(HealthIntelligenceService healthIntelligenceService,
                                            GuidanceServiceClient guidanceClient,
                                            NompiloSignalService signalService,
                                            PiiAccessAuditService auditService) {
        this.healthIntelligenceService = healthIntelligenceService;
        this.guidanceClient = guidanceClient;
        this.signalService = signalService;
        this.auditService = auditService;
    }

    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(required = false) String work_mode,
            @RequestParam(required = false) String facility_id,
            @RequestParam(required = false) String shift_id) {

        try {
            List<Map<String, Object>> notifications = new ArrayList<>();
            notifications.addAll(buildContextualNotifications(work_mode, tenantId));
            notifications.addAll(healthIntelligenceService.buildAssistantNotifications(
                    work_mode, tenantId, facility_id, shift_id));
            return ResponseEntity.ok(Map.of("data", notifications));
        } catch (Exception e) {
            log.warn("Assistant notifications aggregation failed: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Assistant notifications aggregation failed",
                    e);
        }
    }

    /**
     * Conversational Nompilo turn. The shell's ProactiveAssistant POSTs a free-text message plus
     * optional context; we enrich it with server-derived trust/signals (mirroring the context engine)
     * and route it through the real guidance / LLM path with the honesty gate intact.
     *
     * <p>Request body:</p>
     * <pre>{ "message": string,
     *   "context"?: { "route"?, "facilityId"?, "journeyStage"?,
     *                 "failure"?: { "code"?, "action"?, "correlationId"? } } }</pre>
     *
     * <p>Response body:</p>
     * <pre>{ "reply": string, "actions"?: [{ "label", "href" }], "degraded": boolean,
     *   "disclaimer"?: string, "sources"?: [...], "answerKind": string }</pre>
     *
     * <p>{@code degraded=true} (with an honest reply, never a fabricated one) when the guidance/LLM
     * path is unavailable or the honesty gate blocked a real answer. {@code answerKind} distinguishes
     * general guidance from a failure-recovery explanation and from an honest no-answer.</p>
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.SUBJECT_ID, required = false) String subjectId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        Map<String, Object> b = body == null ? Map.of() : body;
        String message = str(b, "message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "reply", "Ask me a question and I'll do my best to guide you.",
                    "degraded", true,
                    "answerKind", "empty",
                    "disclaimer", NOMPILO_DISCLAIMER));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = b.get("context") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : Map.of();
        String routePath = firstNonBlank(str(ctx, "route"), str(ctx, "routePath"));
        String userType = userType(actorType);
        boolean delegated = subjectId != null && !subjectId.isBlank() && !subjectId.equals(actorId);
        boolean citizenSelf = "CITIZEN".equals(userType) && !delegated;

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = ctx.get("failure") instanceof Map<?, ?> fm
                ? (Map<String, Object>) fm : null;
        String failureCode = failure == null ? null : str(failure, "code");

        // A failed citizen action: bias toward recovery via the real explain-locked guidance path.
        if (failureCode != null && !failureCode.isBlank()) {
            return failureRecovery(tenantId, actorId, actorType, subjectId, facilityId,
                    routePath, userType, failureCode, ctx, delegated, request, requestId, correlationId);
        }

        // Normal conversational turn → real guidance /ask (retrieval or grounded LLM behind the gate).
        try {
            Map<String, Object> askCtx = new LinkedHashMap<>();
            if (routePath != null) askCtx.put("routePath", routePath);
            String ctxFacility = firstNonBlank(str(ctx, "facilityId"), facilityId);
            if (ctxFacility != null) askCtx.put("facilityId", ctxFacility);
            if (str(ctx, "journeyStage") != null) askCtx.put("journeyStage", str(ctx, "journeyStage"));
            askCtx.put("userType", userType);

            // Server-derived trust signals (mirror the context engine) — never trusted from the client.
            if (citizenSelf && actorId != null) {
                CitizenSignals derived = signalService.citizenSignals(actorId, false);
                askCtx.put("trustLoa", derived.trustLoa());
                askCtx.put("signals", new ArrayList<>(derived.signals()));
            }

            JsonNode result = guidanceClient.ask(message, citizenSelf, askCtx);
            // Never log the message content (may carry PII); audit the interaction by route only.
            audit(tenantId, actorId, actorType, delegated ? subjectId : actorId,
                    "NOMPILO_ASSISTANT_CHAT", routePath, request, facilityId);
            return ResponseEntity.ok(fromGuidanceAnswer(result));
        } catch (Exception e) {
            log.warn("Assistant chat guidance path failed: {}", e.getMessage());
            return ResponseEntity.ok(unavailable(routePath));
        }
    }

    /**
     * Failure-aware turn: resolve a safe plain-language explanation of the failed/locked action via
     * guidance {@code explain-locked}, then offer real recovery next-steps (the guidance CTA, retry
     * the originating route, report a problem, contact support). Always audited (sensitive path).
     */
    private ResponseEntity<Map<String, Object>> failureRecovery(
            String tenantId, String actorId, String actorType, String subjectId, String facilityId,
            String routePath, String userType, String failureCode, Map<String, Object> ctx,
            boolean delegated, HttpServletRequest request, String requestId, String correlationId) {
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("reasonCode", failureCode);
            req.put("userType", userType);
            if (routePath != null) req.put("routePath", routePath);
            JsonNode explained = guidanceClient.explainLocked(req);

            audit(tenantId, actorId, actorType, delegated ? subjectId : actorId,
                    "NOMPILO_ASSISTANT_FAILURE_RECOVERY", routePath, request, facilityId);

            String title = text(explained, "title");
            String explanation = text(explained, "explanation");
            String reply = explanation != null ? explanation
                    : "That action didn't go through. Here's what you can try next.";

            List<Map<String, Object>> actions = new ArrayList<>();
            addAction(actions, text(explained, "ctaLabel"), text(explained, "ctaRoute"));
            // Retry the originating route when we have one; then the always-available recovery lanes.
            addAction(actions, "Try again", routePath);
            addAction(actions, "Report a problem", "/welcome/report");
            addAction(actions, "Get help", "/help");

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("reply", title != null && explanation != null ? title + ": " + explanation : reply);
            out.put("actions", actions);
            out.put("degraded", explanation == null);
            out.put("answerKind", "failure-recovery");
            out.put("disclaimer", NOMPILO_DISCLAIMER);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.warn("Assistant chat failure-recovery path failed: {}", e.getMessage());
            Map<String, Object> out = unavailable(routePath);
            out.put("reply", "That action didn't complete and I can't reach my guidance right now. "
                    + "You can try again, report a problem, or get help.");
            out.put("answerKind", "failure-recovery");
            List<Map<String, Object>> actions = new ArrayList<>();
            addAction(actions, "Try again", routePath);
            addAction(actions, "Report a problem", "/welcome/report");
            addAction(actions, "Get help", "/help");
            out.put("actions", actions);
            return ResponseEntity.ok(out);
        }
    }

    /**
     * Shape the guidance {@code /ask} answer into the assistant chat contract. Honesty gate is honoured
     * downstream: {@code answerSource} is {@code llm} (real grounded model), {@code retrieval} (real
     * knowledge base) or {@code none} (honest no-answer). We mark {@code degraded=true} only for
     * {@code none}, and we never rewrite the text into a confident-sounding answer.
     */
    private Map<String, Object> fromGuidanceAnswer(JsonNode result) {
        String answerSource = result != null ? text(result, "answerSource") : null;
        String replyText = result != null ? text(result, "response") : null;
        boolean degraded = replyText == null || "none".equals(answerSource);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reply", replyText != null ? replyText
                : "I don't have information about that yet. Please consult your healthcare provider, "
                        + "or browse the health topics for related guidance.");

        // Real next-step links: source articles that carry a URL, else a real route into guidance.
        List<Map<String, Object>> actions = new ArrayList<>();
        List<Map<String, Object>> sources = new ArrayList<>();
        if (result != null && result.get("sources") != null && result.get("sources").isArray()) {
            for (JsonNode s : result.get("sources")) {
                Map<String, Object> src = new LinkedHashMap<>();
                src.put("title", asText(s.get("title")));
                src.put("type", asText(s.get("type")));
                src.put("url", asText(s.get("url")));
                sources.add(src);
                String url = asText(s.get("url"));
                if (url != null && !url.isBlank()) {
                    addAction(actions, asText(s.get("title")), url);
                }
            }
        }
        if (actions.isEmpty()) {
            addAction(actions, "Explore health guidance", "/guidance");
        }

        out.put("actions", actions);
        out.put("sources", sources);
        out.put("degraded", degraded);
        // llm/retrieval are both real general health guidance; none is an honest "I don't know".
        out.put("answerKind", "none".equals(answerSource) ? "unavailable" : "general-guidance");
        out.put("answerSource", answerSource != null ? answerSource : "none");
        out.put("disclaimer", NOMPILO_DISCLAIMER);
        return out;
    }

    /** Honest degraded reply when the guidance/LLM path can't be reached at all — never fabricated. */
    private Map<String, Object> unavailable(String routePath) {
        List<Map<String, Object>> actions = new ArrayList<>();
        addAction(actions, "Explore health guidance", "/guidance");
        addAction(actions, "Report a problem", "/welcome/report");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reply", "I'm having trouble reaching my guidance right now, so I can't answer that fully. "
                + "You can try again in a moment, browse health topics, or report a problem.");
        out.put("actions", actions);
        out.put("degraded", true);
        out.put("answerKind", "unavailable");
        out.put("disclaimer", NOMPILO_DISCLAIMER);
        return out;
    }

    private void audit(String tenantId, String actorId, String actorType, String subjectId,
                       String action, String routePath, HttpServletRequest request, String facilityId) {
        try {
            auditService.recordAccess(
                    tenantId,
                    actorId,
                    actorType != null ? actorType : "CITIZEN",
                    subjectId,
                    "PATIENT",
                    action,
                    // Never the message text (may carry PII) — the route is the only resource recorded.
                    new String[]{routePath != null ? routePath : "assistant-chat"},
                    "GUIDANCE",
                    channel(request),
                    "MINIMAL",
                    request != null ? request.getRemoteAddr() : null,
                    request != null ? request.getHeader("User-Agent") : null,
                    facilityId);
        } catch (Exception e) {
            log.debug("Assistant chat audit skipped: {}", e.getMessage());
        }
    }

    private static String channel(HttpServletRequest request) {
        if (request == null) return "WEB";
        String src = request.getHeader("X-Request-Source");
        return src != null && src.toLowerCase().contains("mobile") ? "APP" : "WEB";
    }

    private static String userType(String actorType) {
        if (actorType == null) return "CITIZEN";
        return switch (actorType.toUpperCase()) {
            case "PROVIDER" -> "PROVIDER";
            case "CAREGIVER", "GUARDIAN" -> "GUARDIAN";
            case "OPERATOR", "FACILITY_ADMIN" -> "FACILITY_ADMIN";
            case "SUPPORT" -> "SUPPORT";
            default -> "CITIZEN";
        };
    }

    private static void addAction(List<Map<String, Object>> actions, String label, String href) {
        if (label == null || label.isBlank() || href == null || href.isBlank()) return;
        // De-duplicate by href so retry/CTA don't collide.
        for (Map<String, Object> a : actions) {
            if (href.equals(a.get("href"))) return;
        }
        actions.add(Map.of("label", label, "href", href));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        return b != null && !b.isBlank() ? b : null;
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String asText(JsonNode v) {
        return v == null || v.isNull() ? null : v.asText();
    }

    private List<Map<String, Object>> buildContextualNotifications(String workMode, String tenantId) {
        if (workMode == null) {
            return List.of();
        }
        return switch (workMode) {
            case "clinical" -> List.of(
                    notification("OPERATIONAL", "INFO",
                            "Clinical Mode Active",
                            "Patient records, encounters, and orders are available in this mode.",
                            false)
            );
            case "wellness" -> List.of(
                    notification("WELLNESS", "INFO",
                            "Wellness Mode",
                            "Track your health goals, nutrition, and activity in this mode.",
                            true)
            );
            default -> List.of();
        };
    }

    private static Map<String, Object> notification(
            String type, String severity, String title, String body, boolean dismissible) {
        return Map.of(
                "id", UUID.randomUUID().toString(),
                "type", type,
                "severity", severity,
                "title", title,
                "body", body,
                "dismissible", dismissible,
                "timestamp", OffsetDateTime.now().toString(),
                "source", "assistant-bff"
        );
    }
}
