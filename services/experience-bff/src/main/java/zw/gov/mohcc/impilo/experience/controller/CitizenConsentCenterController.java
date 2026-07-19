package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient;
import zw.gov.mohcc.impilo.experience.service.SubjectResolutionService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Citizen Consent Center (Identity Journey Doctrine, CJ13) — one surface answering the two
 * questions a person asks about their record: <b>"who did I allow?"</b> (my consent directives)
 * and <b>"who actually looked?"</b> (my record access-log). It composes the two sovereign
 * services — tshepo-consent (directives) and tshepo-audit (immutable access history) — that
 * were previously only reachable piecemeal, and with no citizen access-log surface at all.
 *
 * <p><b>Subject safety:</b> the consent directives are fetched via the consent service's
 * trust-context self-resolving portal endpoint (the subject is never client-supplied, and
 * ownership is enforced there), and the access-log subject (CPID) is resolved <b>server-side</b>
 * from the authenticated actor — so a citizen can only ever see their own consents and their
 * own access history, and the internal CPID never reaches the browser.</p>
 */
@RestController
@RequestMapping("/internal/v1/citizen/consent-center")
public class CitizenConsentCenterController {

    private static final Logger log = LoggerFactory.getLogger(CitizenConsentCenterController.class);

    private final TshepoConsentServiceClient consentClient;
    private final TshepoAuditServiceClient auditClient;
    private final SubjectResolutionService subjectResolution;

    public CitizenConsentCenterController(TshepoConsentServiceClient consentClient,
                                          TshepoAuditServiceClient auditClient,
                                          SubjectResolutionService subjectResolution) {
        this.consentClient = consentClient;
        this.auditClient = auditClient;
        this.subjectResolution = subjectResolution;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> consentCenter(
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Map<String, Object> data = new LinkedHashMap<>();

        // "Who did I allow?" — the consent service self-resolves the trust-context patient.
        data.put("consents", safe(() -> consentClient.myConsents(page, size), "consents"));

        // "Who actually looked?" — the access-log is CPID-keyed; resolve the CPID from the
        // authenticated actor (citizen actorId == healthId). Degrades gracefully to empty.
        JsonNode accessLog = null;
        String cpid = actorId == null ? null : subjectResolution.cpidForHealthId(actorId);
        if (cpid != null) {
            accessLog = safe(() -> auditClient.getAccessHistory(cpid, page, size), "access-log");
        } else if (actorId != null) {
            log.debug("consent-center: no CPID resolved for the actor; access-log omitted");
        }
        data.put("accessLog", accessLog);

        return ResponseEntity.ok(envelope(data, requestId, correlationId));
    }

    /**
     * Revoke one of the citizen's own consent directives. Ownership is verified by the consent
     * service against the trust-context patient — a citizen cannot revoke another's consent.
     */
    @PostMapping("/revoke/{consentId}")
    public ResponseEntity<Map<String, Object>> revoke(
            @PathVariable UUID consentId,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            JsonNode result = consentClient.revokeMyConsent(consentId);
            data.put("status", "REVOKED");
            data.put("consent", result);
            return ResponseEntity.ok(envelope(data, requestId, correlationId));
        } catch (Exception e) {
            log.warn("consent-center revoke failed for {}: {}", consentId, e.getMessage());
            data.put("status", "ERROR");
            data.put("message", "The consent could not be revoked. Please try again.");
            return ResponseEntity.status(502).body(envelope(data, requestId, correlationId));
        }
    }

    /** Wrap in the house {@code {data, meta}} envelope the experience shell's api-client expects. */
    private static Map<String, Object> envelope(Object data, String requestId, String correlationId) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("request_id", requestId);
        meta.put("correlation_id", correlationId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", meta);
        return body;
    }

    /** Fetch that degrades to null (never fails the whole surface if one source is down). */
    private JsonNode safe(java.util.function.Supplier<JsonNode> call, String label) {
        try {
            return call.get();
        } catch (Exception e) {
            log.warn("consent-center {} source unavailable: {}", label, e.getMessage());
            return null;
        }
    }
}
