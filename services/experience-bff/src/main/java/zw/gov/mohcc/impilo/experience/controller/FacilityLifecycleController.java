package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoFacilityLifecycleClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Facility lifecycle BFF glue — REGISTRATION, VERIFICATION, TRUST dimensions,
 * and GOVERNANCE cases (facility claim has its own {@code FacilityClaimController}).
 * Composition only; TUSO is the facility SoR.
 *
 * <p>Doctrine (Place Journey Doctrine §2 / D-L4/D-L6/D-L8):</p>
 * <ul>
 *   <li>The applicant is ALWAYS the session Health ID (X-Actor-ID) — never taken
 *       from the request body.</li>
 *   <li>Registration and opening a verification case require a proven personal
 *       identity (≥LOA2, consumed from the Wave-G person-proofing spine): knowing
 *       a facility's details is never enough to act on it.</li>
 *   <li>Raw evidence references are masked at the edge before travelling
 *       downstream; downstream verdicts propagate verbatim (never faked, never
 *       remapped).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/facility-lifecycle")
public class FacilityLifecycleController {

    private static final Logger log = LoggerFactory.getLogger(FacilityLifecycleController.class);

    /** Person half of proof-of-authority (D-L4): submit actions require ≥LOA2. */
    private static final int MIN_SUBMIT_ASSURANCE_RANK = 2;

    private static final Set<String> GOVERNANCE_ACTIONS =
            Set.of("high-risk-update", "transfer", "duplicate-report");

    private final TusoFacilityLifecycleClient tusoClient;
    private final IdentityAssuranceServiceClient assuranceClient;

    public FacilityLifecycleController(TusoFacilityLifecycleClient tusoClient,
                                       IdentityAssuranceServiceClient assuranceClient) {
        this.tusoClient = tusoClient;
        this.assuranceClient = assuranceClient;
    }

    // ── Registration ───────────────────────────────────────────────────────

    /**
     * Register a new facility not yet on the platform. The applicant is the
     * session Health ID. Requires a proven personal identity (≥LOA2).
     */
    @PostMapping("/registrations")
    public ResponseEntity<Map<String, Object>> registerFacility(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {

        String name = str(body.get("name"));
        if (name == null) {
            return error(HttpStatus.BAD_REQUEST, "FACILITY_NAME_REQUIRED",
                    "A facility name is required to register a facility.", requestId, correlationId);
        }
        if (submitAssuranceRank() < MIN_SUBMIT_ASSURANCE_RANK) {
            return identityProofingRequired(requestId, correlationId);
        }
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("personHealthId", actorId); // applicant is ALWAYS the session identity
            req.put("name", name);
            copyIfPresent(body, req, "facilityType", "province", "district", "ownershipCategory", "notes");
            if (body.get("latitude") != null) req.put("latitude", body.get("latitude"));
            if (body.get("longitude") != null) req.put("longitude", body.get("longitude"));
            if (body.get("identifiers") instanceof Map<?, ?> ids) req.put("identifiers", ids);
            String evidenceRef = str(body.get("evidenceRef"));
            if (evidenceRef != null) req.put("evidenceRef", maskRef(evidenceRef));

            JsonNode result = tusoClient.submitRegistration(req);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("submitted", true);
            data.put("caseRef", text(result, "caseRef"));
            data.put("status", text(result, "status"));
            data.put("note", "Your facility registration was received and is under review. It is not "
                    + "listed or usable until the registry confirms it.");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(envelope(data, requestId, correlationId));
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    /** Steward registration review queue by outcome. Upstream enforces steward gating (X-Actor-Type). */
    @GetMapping("/registrations")
    public ResponseEntity<Map<String, Object>> registrationCases(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(value = "outcome", defaultValue = "DUPLICATE_REVIEW") String outcome) {
        try {
            JsonNode result = tusoClient.registrationCases(outcome);
            return ok(Map.of("cases", casesArray(result)), requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Verification ───────────────────────────────────────────────────────

    /**
     * Open a facility verification case (evidence + claimed GPS vs the Ndila
     * anchor, upstream). Requires a proven personal identity (≥LOA2).
     */
    @PostMapping("/{facilityUuid}/verification-cases")
    public ResponseEntity<Map<String, Object>> openVerificationCase(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @PathVariable String facilityUuid,
            @RequestBody Map<String, Object> body) {

        String verificationType = str(body.get("verificationType"));
        if (verificationType == null) {
            return error(HttpStatus.BAD_REQUEST, "VERIFICATION_TYPE_REQUIRED",
                    "A verification type is required to open a verification case.", requestId, correlationId);
        }
        if (submitAssuranceRank() < MIN_SUBMIT_ASSURANCE_RANK) {
            return identityProofingRequired(requestId, correlationId);
        }
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("verificationType", verificationType);
            if (body.get("evidenceRefs") instanceof List<?> refs) {
                req.put("evidenceRefs", refs.stream().map(r -> maskRef(String.valueOf(r))).toList());
            }
            if (body.get("claimedLatitude") != null) req.put("claimedLatitude", body.get("claimedLatitude"));
            if (body.get("claimedLongitude") != null) req.put("claimedLongitude", body.get("claimedLongitude"));
            String rtcSessionRef = str(body.get("rtcSessionRef"));
            if (rtcSessionRef != null) req.put("rtcSessionRef", rtcSessionRef);

            JsonNode result = tusoClient.openVerificationCase(facilityUuid, req);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(envelope(verificationView(result), requestId, correlationId));
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    /** Verification cases for a facility (verifier-gated upstream). */
    @GetMapping("/{facilityUuid}/verification-cases")
    public ResponseEntity<Map<String, Object>> verificationCases(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String facilityUuid) {
        try {
            JsonNode result = tusoClient.verificationCases(facilityUuid);
            return ok(Map.of("cases", casesArray(result)), requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    /**
     * Record a verification-case decision (steward/verifier action). Upstream
     * enforces the verifier gate on X-Actor-Type; the decision is bound to the
     * session identity as the decider.
     */
    @PostMapping("/{facilityUuid}/verification-cases/{caseId}/decision")
    public ResponseEntity<Map<String, Object>> decideVerificationCase(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @PathVariable String facilityUuid,
            @PathVariable String caseId,
            @RequestBody Map<String, Object> body) {

        String decision = str(body.get("decision"));
        if (decision == null) {
            return error(HttpStatus.BAD_REQUEST, "DECISION_REQUIRED",
                    "A decision is required (for example APPROVED or REJECTED).", requestId, correlationId);
        }
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("decision", decision);
            if (str(body.get("note")) != null) req.put("note", str(body.get("note")));
            JsonNode result = tusoClient.decideVerificationCase(facilityUuid, caseId, req);
            return ok(verificationView(result), requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Trust dimensions ───────────────────────────────────────────────────

    /** The facility's graded trust dimensions (transparency read). */
    @GetMapping("/{facilityUuid}/trust-dimensions")
    public ResponseEntity<Map<String, Object>> trustDimensions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String facilityUuid) {
        try {
            JsonNode result = tusoClient.trustDimensions(facilityUuid);
            return ok(Map.of("dimensions", dimensionsArray(result)), requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    /** Recompute the facility's trust dimensions. */
    @PostMapping("/{facilityUuid}/trust-dimensions/recompute")
    public ResponseEntity<Map<String, Object>> recomputeTrustDimensions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String facilityUuid) {
        try {
            JsonNode result = tusoClient.recomputeTrustDimensions(facilityUuid);
            return ok(Map.of("dimensions", dimensionsArray(result)), requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Completeness + data-gap tasks (V036 / HPA enrichment) ───────────────

    /** Per-dimension completeness for a facility (identity / regulatory / location / …). */
    @GetMapping("/{facilityUuid}/completeness")
    public ResponseEntity<Map<String, Object>> completeness(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String facilityUuid) {
        try {
            JsonNode result = tusoClient.completeness(facilityUuid);
            return ok(Map.of("dimensions", casesArray(result)), requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    /** Actionable data-gap / verification tasks for a facility. */
    @GetMapping("/{facilityUuid}/data-gaps")
    public ResponseEntity<Map<String, Object>> dataGaps(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String facilityUuid) {
        try {
            JsonNode result = tusoClient.dataGaps(facilityUuid);
            return ok(Map.of("tasks", casesArray(result)), requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    /** Move a data-gap task forward (IN_PROGRESS / RESOLVED / DISMISSED) with an audit note. */
    @PostMapping("/{facilityUuid}/data-gaps/{taskId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveDataGap(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String facilityUuid,
            @PathVariable String taskId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode result = tusoClient.resolveDataGap(facilityUuid, taskId,
                    body != null ? body : new LinkedHashMap<>());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", text(result, "id"));
            data.put("status", text(result, "status"));
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Governance ─────────────────────────────────────────────────────────

    /**
     * Open a facility governance case: high-risk-update | transfer |
     * duplicate-report. The applicant is the session Health ID; the change
     * payload is passed through and adjudicated by a steward downstream.
     */
    @PostMapping("/{facilityUuid}/governance/{action}")
    public ResponseEntity<Map<String, Object>> openGovernanceCase(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @PathVariable String facilityUuid,
            @PathVariable String action,
            @RequestBody(required = false) Map<String, Object> body) {

        String normalised = action == null ? "" : action.toLowerCase(Locale.ROOT);
        if (!GOVERNANCE_ACTIONS.contains(normalised)) {
            return error(HttpStatus.BAD_REQUEST, "UNSUPPORTED_GOVERNANCE_ACTION",
                    "Supported governance actions are: high-risk-update, transfer, duplicate-report.",
                    requestId, correlationId);
        }
        try {
            Map<String, Object> payload = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
            payload.put("requestedBy", actorId); // applicant is the session identity
            JsonNode result = tusoClient.openGovernanceCase(facilityUuid, normalised, payload);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(envelope(governanceView(result), requestId, correlationId));
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    /** Governance cases for a facility by type (steward-gated upstream). */
    @GetMapping("/{facilityUuid}/governance/cases")
    public ResponseEntity<Map<String, Object>> governanceCases(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String facilityUuid,
            @RequestParam(value = "caseType", defaultValue = "HIGH_RISK_UPDATE") String caseType) {
        try {
            JsonNode result = tusoClient.governanceCases(facilityUuid, caseType);
            return ok(Map.of("cases", casesArray(result)), requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    /**
     * Record a governance-case decision (steward action). Upstream enforces the
     * steward gate on X-Actor-Type; no change takes effect until it is approved.
     */
    @PostMapping("/{facilityUuid}/governance/cases/{caseId}/decision")
    public ResponseEntity<Map<String, Object>> decideGovernanceCase(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @PathVariable String facilityUuid,
            @PathVariable String caseId,
            @RequestBody Map<String, Object> body) {

        String decision = str(body.get("decision"));
        if (decision == null) {
            return error(HttpStatus.BAD_REQUEST, "DECISION_REQUIRED",
                    "A decision is required (for example APPROVED or REJECTED).", requestId, correlationId);
        }
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("decision", decision);
            if (str(body.get("note")) != null) req.put("note", str(body.get("note")));
            JsonNode result = tusoClient.decideGovernanceCase(facilityUuid, caseId, req);
            return ok(governanceView(result), requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Views ──────────────────────────────────────────────────────────────

    private Map<String, Object> verificationView(JsonNode c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("caseRef", text(c, "caseRef"));
        v.put("facilityUuid", text(c, "facilityUuid"));
        v.put("verificationType", text(c, "verificationType"));
        v.put("status", text(c, "status"));
        v.put("gpsDistanceMeters", c != null ? c.path("gpsDistanceMeters") : null);
        v.put("decidedBy", maskId(text(c, "decidedBy")));
        v.put("decidedAt", text(c, "decidedAt"));
        v.put("createdAt", text(c, "createdAt"));
        return v;
    }

    private Map<String, Object> governanceView(JsonNode c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("caseRef", text(c, "caseRef"));
        v.put("caseType", text(c, "caseType"));
        v.put("facilityUuid", text(c, "facilityUuid"));
        v.put("status", text(c, "status"));
        v.put("createdAt", text(c, "createdAt"));
        return v;
    }

    private List<JsonNode> casesArray(JsonNode result) {
        JsonNode arr = result == null ? null : result.path("cases");
        List<JsonNode> out = new java.util.ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(out::add);
        } else if (result != null && result.isArray()) {
            result.forEach(out::add);
        }
        return out;
    }

    private List<JsonNode> dimensionsArray(JsonNode result) {
        JsonNode arr = result == null ? null : result.path("dimensions");
        List<JsonNode> out = new java.util.ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(out::add);
        } else if (result != null && result.isArray()) {
            result.forEach(out::add);
        }
        return out;
    }

    // ── Assurance gate ─────────────────────────────────────────────────────

    private int submitAssuranceRank() {
        try {
            JsonNode status = assuranceClient.getAssuranceStatus();
            String level = text(status, "currentLevel");
            return switch (level == null ? "LOA1" : level.trim().toUpperCase(Locale.ROOT)) {
                case "LOA4" -> 4;
                case "LOA3" -> 3;
                case "LOA2" -> 2;
                default -> 1;
            };
        } catch (Exception e) {
            log.warn("assurance status unavailable — treating as LOA1 (fail-closed): {}", e.getMessage());
            return 1;
        }
    }

    private ResponseEntity<Map<String, Object>> identityProofingRequired(String requestId, String correlationId) {
        return error(HttpStatus.FORBIDDEN, "IDENTITY_PROOFING_REQUIRED",
                "Confirm your personal identity before registering or verifying a facility. "
                        + "Complete identity verification, then try again.",
                requestId, correlationId);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> ok(Map<String, Object> data, String requestId, String correlationId) {
        return ResponseEntity.ok(envelope(data, requestId, correlationId));
    }

    private Map<String, Object> envelope(Map<String, Object> data, String requestId, String correlationId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return response;
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> propagate(
            HttpStatusCodeException e, String requestId, String correlationId) {
        String message = e.getResponseBodyAsString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "error", Map.of(
                        "code", "UPSTREAM_" + e.getStatusCode().value(),
                        "message", message != null && !message.isBlank() ? message : e.getStatusText()),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String... keys) {
        for (String k : keys) {
            String v = str(from.get(k));
            if (v != null) to.put(k, v);
        }
    }

    static String maskId(String id) {
        if (id == null || id.isBlank()) return null;
        String v = id.trim();
        if (v.length() <= 6) return v.charAt(0) + "***";
        return v.substring(0, 4) + "***" + v.substring(v.length() - 2);
    }

    static String maskRef(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        return (v.length() <= 2 ? v : v.substring(0, 2)) + "***";
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String str(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
