package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provisional (temporary) Health ID issuance for the citizen onboarding shell.
 *
 * <p>Replaces the shell's client-side {@code generateTmpHealthId()} fabrication
 * ({@code TMP-xxxx-xxxx}) with a <b>real VITO-issued provisional identity</b>. The TEMPORARY
 * assurance tier in the onboarding status page corresponds to a VITO {@code PROVISIONAL}
 * client: a real {@code health_id} (CPID) + sovereign DID, issued with minimal proofing
 * (IAL 0), that a citizen later upgrades to a permanent/VERIFIED identity at a facility.</p>
 *
 * <h3>Why the canonical register path (not the offline registry token)</h3>
 * <p>VITO exposes two "provisional" mechanisms:</p>
 * <ul>
 *   <li>{@code POST /v1/registry/provisional/issue} — an offline registry {@code PROV-…} token
 *       with a persisted server expiry, but its {@code provisional_id.facility_id} column is
 *       {@code NOT NULL}. It is designed for registration <em>at a facility</em>; a citizen
 *       self-registering online has no facility, so this path is unusable here.</li>
 *   <li>{@code POST /v1/identity/register} — the golden identity path: creates a
 *       {@code PROVISIONAL} client, generates the DID, and anchors the person. No facility
 *       required. This is what we use.</li>
 * </ul>
 *
 * <h3>Honesty contract</h3>
 * <ul>
 *   <li>Never fabricates an id. If the early citizen has not supplied the minimum identity
 *       details VITO needs to anchor a person, the endpoint returns an honest
 *       {@code status: "PENDING"} with the real next step — no fake id.</li>
 *   <li>The expiry is <b>server-authoritative</b>: computed here from the governed provisional
 *       validity policy ({@code impilo.identity.provisional-health-id.validity-days}), not a
 *       browser 90-day guess.</li>
 *   <li>Idempotent by caller-supplied anchor: if the shell has already been issued a provisional
 *       Health ID it passes {@code existingHealthId}; the endpoint then <em>fetches</em> that
 *       identity from VITO and returns it rather than minting a duplicate client.</li>
 *   <li>No PII in logs (only the request id / non-identifying status is logged).</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/identity/health-id")
public class ProvisionalHealthIdController {

    private static final Logger log = LoggerFactory.getLogger(ProvisionalHealthIdController.class);

    private static final String ASSURANCE_TEMPORARY = "TEMPORARY";

    private final VitoServiceClient vitoServiceClient;

    /** Governed validity window for a provisional Health ID (days). Server-authoritative. */
    private final long provisionalValidityDays;

    public ProvisionalHealthIdController(
            VitoServiceClient vitoServiceClient,
            @Value("${impilo.identity.provisional-health-id.validity-days:90}") long provisionalValidityDays) {
        this.vitoServiceClient = vitoServiceClient;
        this.provisionalValidityDays = provisionalValidityDays;
    }

    /**
     * Issue (or fetch, if already issued) a provisional Health ID for the current citizen.
     *
     * <p>Request body (all optional):</p>
     * <pre>{
     *   "existingHealthId": "…",  // if the shell was already issued one — fetch, don't re-mint
     *   "givenName":  "…",
     *   "familyName": "…",
     *   "dateOfBirth":"1990-01-31",
     *   "sex":        "M",
     *   "contact":    "+263…"      // reserved; not persisted by the golden path yet
     * }</pre>
     *
     * <p>Response (issued):</p>
     * <pre>{
     *   "provisionalHealthId": "…",   // REAL VITO CPID
     *   "did":                 "did:impilo:…",
     *   "impiloId":            null,   // human-facing #########X alias is issued later, at approval
     *   "status":              "PROVISIONAL",
     *   "assuranceLevel":      "TEMPORARY",
     *   "issuedAt":            "2026-07-17T…Z",
     *   "expiresAt":           "2026-10-15T…Z",  // server-authoritative
     *   "nextStep":            "VISIT_FACILITY_TO_VERIFY"
     * }</pre>
     *
     * <p>Response (insufficient identity — honest PENDING, no fabricated id):</p>
     * <pre>{
     *   "provisionalHealthId": null,
     *   "status":              "PENDING",
     *   "assuranceLevel":      "TEMPORARY",
     *   "reason":              "IDENTITY_DETAILS_REQUIRED",
     *   "nextStep":            "PROVIDE_IDENTITY_DETAILS",
     *   "requires":            ["givenName","familyName"]
     * }</pre>
     */
    @PostMapping(value = "/provisional", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> issueProvisional(
            @RequestBody(required = false) ProvisionalRequest request,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {

        ProvisionalRequest req = request != null ? request : new ProvisionalRequest();

        // ── Idempotent fetch: the shell already holds a provisional id → return it, never re-mint.
        if (isPresent(req.existingHealthId)) {
            try {
                JsonNode existing = vitoServiceClient.getPatient(req.existingHealthId.trim());
                if (existing != null) {
                    log.info("Provisional Health ID fetch (already issued), requestId={}", requestId);
                    return ok(fetched(existing, req.existingHealthId.trim()), requestId, correlationId);
                }
            } catch (HttpStatusCodeException ex) {
                // Fall through to honest error only if it was a hard failure; a 404 means the
                // supplied id is unknown — surface that rather than silently minting a new one.
                return propagate(ex, requestId, correlationId);
            }
        }

        // ── Minimum identity to anchor a real person. Without it we return an honest PENDING,
        //    never a fabricated id (doctrine: care before coverage, but never a fake identity).
        if (!isPresent(req.givenName) && !isPresent(req.familyName)) {
            log.info("Provisional Health ID pending (identity details required), requestId={}", requestId);
            Map<String, Object> pending = new LinkedHashMap<>();
            pending.put("provisionalHealthId", null);
            pending.put("status", "PENDING");
            pending.put("assuranceLevel", ASSURANCE_TEMPORARY);
            pending.put("reason", "IDENTITY_DETAILS_REQUIRED");
            pending.put("nextStep", "PROVIDE_IDENTITY_DETAILS");
            pending.put("requires", java.util.List.of("givenName", "familyName"));
            return ok(pending, requestId, correlationId);
        }

        // ── Issue: canonical VITO golden path (creates PROVISIONAL client + DID, no facility needed).
        Map<String, Object> issuance = new LinkedHashMap<>();
        issuance.put("givenName", trimOrNull(req.givenName));
        issuance.put("familyName", trimOrNull(req.familyName));
        issuance.put("dateOfBirth", trimOrNull(req.dateOfBirth));
        issuance.put("sex", trimOrNull(req.sex));

        try {
            JsonNode issued = vitoServiceClient.registerIdentity(issuance);
            log.info("Provisional Health ID issued via VITO, requestId={}", requestId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(CompanionHeaders.REQUEST_ID, safe(requestId))
                    .header(CompanionHeaders.CORRELATION_ID, safe(correlationId))
                    .body(minted(issued));
        } catch (HttpStatusCodeException ex) {
            return propagate(ex, requestId, correlationId);
        }
    }

    // ── Response shaping ────────────────────────────────────────────────────

    private Map<String, Object> minted(JsonNode issued) {
        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provisionalHealthId", text(issued, "healthId"));
        body.put("did", text(issued, "did"));
        // Human-facing IMPILO_ID (#########X) alias is only minted at issuance approval, not now.
        body.put("impiloId", null);
        String status = text(issued, "status");
        body.put("status", status != null ? status : "PROVISIONAL");
        body.put("assuranceLevel", ASSURANCE_TEMPORARY);
        body.put("issuedAt", now.toString());
        body.put("expiresAt", now.plusDays(provisionalValidityDays).toString());
        body.put("nextStep", "VISIT_FACILITY_TO_VERIFY");
        return body;
    }

    private Map<String, Object> fetched(JsonNode client, String healthId) {
        Map<String, Object> body = new LinkedHashMap<>();
        String resolvedId = text(client, "healthId");
        body.put("provisionalHealthId", resolvedId != null ? resolvedId : healthId);
        body.put("did", text(client, "did"));
        body.put("impiloId", text(client, "impiloId"));
        String status = text(client, "status");
        body.put("status", status != null ? status : "PROVISIONAL");
        body.put("assuranceLevel", ASSURANCE_TEMPORARY);
        // A previously-issued identity does not re-start its clock; expose the governed window
        // relative to its issuance if VITO surfaced one, else omit (honest — no re-computation).
        String issuedAt = text(client, "createdAt");
        if (issuedAt != null) {
            body.put("issuedAt", issuedAt);
        }
        body.put("nextStep", "VISIT_FACILITY_TO_VERIFY");
        return body;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static ResponseEntity<Map<String, Object>> ok(
            Map<String, Object> body, String requestId, String correlationId) {
        return ResponseEntity.ok()
                .header(CompanionHeaders.REQUEST_ID, safe(requestId))
                .header(CompanionHeaders.CORRELATION_ID, safe(correlationId))
                .body(body);
    }

    private static ResponseEntity<Map<String, Object>> propagate(
            HttpStatusCodeException ex, String requestId, String correlationId) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("status", "ERROR");
        err.put("code", ex.getStatusCode().value());
        err.put("message", "Provisional Health ID issuance failed upstream");
        return ResponseEntity.status(ex.getStatusCode())
                .header(CompanionHeaders.REQUEST_ID, safe(requestId))
                .header(CompanionHeaders.CORRELATION_ID, safe(correlationId))
                .body(err);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private static boolean isPresent(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String trimOrNull(String s) {
        return isPresent(s) ? s.trim() : null;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    /** Request body — all fields optional; snake/camel tolerant via default binding. */
    public static class ProvisionalRequest {
        public String existingHealthId;
        public String givenName;
        public String familyName;
        public String dateOfBirth;
        public String sex;
        public String contact;
    }
}
