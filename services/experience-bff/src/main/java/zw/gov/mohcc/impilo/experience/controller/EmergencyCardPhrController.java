package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ButanoServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.service.PiiAccessAuditService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * B5 — governed break-glass card PHR read. An emergency responder presents a
 * patient's SMART card; this resolves it to the subject through the B0 card seam,
 * opens a REAL break-glass request (tshepo-authz — durable, mandatory post-hoc
 * review), reads the subject's emergency health summary, and emits an elevated
 * audit record. It does NOT invent governance: the break-glass request/review
 * lifecycle and the {@code BREAK_GLASS} purpose-of-use consent override are the
 * existing trust-plane primitives.
 *
 * <p>Fail-closed: no card / no reason → 400; the presented card can't be resolved
 * → 422 (never a fabricated identity); the break-glass request can't be opened →
 * 502 and the PHR is NOT read (the read is gated on the governed request); the
 * summary upstream is unavailable → 503. Every successful read is audited as a
 * responder viewing another subject's record under BREAK_GLASS.</p>
 */
@RestController
@RequestMapping("/internal/v1/emergency")
public class EmergencyCardPhrController {

    private static final Logger log = LoggerFactory.getLogger(EmergencyCardPhrController.class);

    private final VitoServiceClient vitoClient;
    private final TshepoAuthzServiceClient authzClient;
    private final ButanoServiceClient butanoClient;
    private final PiiAccessAuditService piiAccessAudit;
    private final ObjectMapper objectMapper;

    public EmergencyCardPhrController(VitoServiceClient vitoClient,
                                      TshepoAuthzServiceClient authzClient,
                                      ButanoServiceClient butanoClient,
                                      PiiAccessAuditService piiAccessAudit,
                                      ObjectMapper objectMapper) {
        this.vitoClient = vitoClient;
        this.authzClient = authzClient;
        this.butanoClient = butanoClient;
        this.piiAccessAudit = piiAccessAudit;
        this.objectMapper = objectMapper;
    }

    /**
     * Break-glass read of a card-holder's emergency health summary.
     * Body: exactly one of {@code cardNumber|qrToken|cardAssertion}, plus a mandatory {@code reason}.
     */
    @PostMapping("/card-phr")
    public ResponseEntity<Map<String, Object>> breakGlassCardPhr(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId) {

        String reason = str(body, "reason");
        if (reason == null) {
            return error(HttpStatus.BAD_REQUEST, "REASON_REQUIRED",
                    "Break-glass reason is mandatory", requestId, correlationId);
        }
        if (actorId == null || actorId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "ACTOR_REQUIRED",
                    "Responder identity (X-Actor-ID) is required for break-glass", requestId, correlationId);
        }
        String cardNumber = str(body, "cardNumber");
        String qrToken = str(body, "qrToken");
        String cardAssertion = str(body, "cardAssertion");
        if (cardNumber == null && qrToken == null && cardAssertion == null) {
            return error(HttpStatus.BAD_REQUEST, "CARD_REQUIRED",
                    "one of cardNumber, qrToken, cardAssertion is required", requestId, correlationId);
        }

        // 1) Resolve the presented card → subject (B0 seam). Never fabricate identity.
        String subjectCpid;
        try {
            JsonNode card = vitoClient.resolveCard(cardNumber, qrToken, cardAssertion);
            subjectCpid = card == null ? null : text(card, "cpid", "healthId");
        } catch (Exception e) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "CARD_NOT_RESOLVED",
                    "The presented card could not be resolved", requestId, correlationId);
        }
        if (subjectCpid == null || subjectCpid.isBlank()) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "CARD_NOT_RESOLVED",
                    "The presented card could not be resolved to a patient", requestId, correlationId);
        }

        // 2) Open a REAL break-glass request (durable, lands in the mandatory review queue).
        //    The PHR read is gated on this — no governed request, no read.
        String breakGlassId;
        try {
            Map<String, Object> bg = new LinkedHashMap<>();
            bg.put("tenantId", UUID.fromString(tenantId));
            bg.put("actorId", actorId);
            bg.put("reason", reason);
            bg.put("resourceType", "IPS_SUMMARY");
            bg.put("resourceId", subjectCpid);
            JsonNode created = authzClient.requestBreakGlass(bg);
            breakGlassId = created == null ? null : text(created, "id", "requestId");
        } catch (Exception e) {
            log.error("Break-glass request failed for card PHR read: {}", e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "BREAK_GLASS_REQUEST_FAILED",
                    "Could not open a break-glass request; emergency read denied", requestId, correlationId);
        }

        // 3) Read the RESOLVED SUBJECT's emergency summary (not the responder's own).
        String summaryJson;
        try {
            var resp = butanoClient.getIpsSummary(subjectCpid);
            summaryJson = resp == null ? null : resp.getBody();
        } catch (Exception e) {
            log.warn("Break-glass IPS read upstream unavailable: {}", e.getMessage());
            return error(HttpStatus.SERVICE_UNAVAILABLE, "SUMMARY_UNAVAILABLE",
                    "The emergency summary is temporarily unavailable", requestId, correlationId);
        }

        // 4) Elevated audit: a responder viewed another subject's record under BREAK_GLASS.
        piiAccessAudit.recordAccess(tenantId, actorId,
                actorType != null ? actorType : "PROVIDER",
                subjectCpid, "PATIENT", "VIEW",
                new String[]{"emergency_summary", "allergies", "conditions", "medications"},
                "BREAK_GLASS", "APP", "MINIMAL", null, null, facilityId);

        JsonNode summary = parse(summaryJson);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subjectCpid", subjectCpid);
        data.put("breakGlassRequestId", breakGlassId);
        data.put("summary", summary);
        data.put("notice", "This emergency access is logged and subject to mandatory review.");
        return ResponseEntity.ok(Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String f : fields) {
            if (node.has(f) && !node.get(f).isNull()) {
                String v = node.get(f).asText(null);
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message,
                                                      String requestId, String correlationId) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
