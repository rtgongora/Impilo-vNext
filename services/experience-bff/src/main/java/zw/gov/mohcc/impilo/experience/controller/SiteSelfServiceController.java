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
import zw.gov.mohcc.impilo.experience.client.IndawoServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Indawo SITE self-service BFF glue — SJ2 site REGISTRATION and SJ1 operator-role
 * GRANTS. Composition only; INDAWO is the site SoR. This is DISTINCT from the
 * regulatory {@code /public-health/site-registry} application rail (licensing /
 * inspection); do not confuse the two.
 *
 * <p>Doctrine (Place Journey Doctrine §2 / D-L4): the applicant is ALWAYS the
 * session Health ID (X-Actor-ID), never the body. Registering a site or claiming
 * an operator role requires a proven personal identity (≥LOA2, from the Wave-G
 * person-proofing spine) plus an evidence reference — the authority half. Raw
 * evidence refs are masked at the edge; downstream verdicts propagate verbatim.</p>
 */
@RestController
@RequestMapping("/api/v1/site-self-service")
public class SiteSelfServiceController {

    private static final Logger log = LoggerFactory.getLogger(SiteSelfServiceController.class);

    private static final int MIN_SUBMIT_ASSURANCE_RANK = 2;

    private static final Set<String> OPERATOR_ROLES =
            Set.of("SITE_VIEWER", "SITE_OPERATOR", "SITE_ADMINISTRATOR", "REGULATORY_LIAISON");

    private final IndawoServiceClient indawoClient;
    private final IdentityAssuranceServiceClient assuranceClient;

    public SiteSelfServiceController(IndawoServiceClient indawoClient,
                                     IdentityAssuranceServiceClient assuranceClient) {
        this.indawoClient = indawoClient;
        this.assuranceClient = assuranceClient;
    }

    // ── Site registration (SJ2) ────────────────────────────────────────────

    @PostMapping("/registrations")
    public ResponseEntity<Map<String, Object>> registerSite(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {

        String name = str(body.get("name"));
        String type = str(body.get("type"));
        if (name == null || type == null) {
            return error(HttpStatus.BAD_REQUEST, "SITE_NAME_TYPE_REQUIRED",
                    "A site name and type are required to register a site.", requestId, correlationId);
        }
        String evidenceRef = str(body.get("evidenceRef"));
        if (evidenceRef == null) {
            return error(HttpStatus.BAD_REQUEST, "EVIDENCE_REQUIRED",
                    "A supporting evidence reference is required to register a site.", requestId, correlationId);
        }
        if (submitAssuranceRank() < MIN_SUBMIT_ASSURANCE_RANK) {
            return identityProofingRequired(requestId, correlationId);
        }
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("personHealthId", actorId); // applicant is ALWAYS the session identity
            req.put("name", name);
            req.put("type", type);
            copyIfPresent(body, req, "siteCategory", "province", "district", "siteCode", "notes");
            req.put("evidenceRef", maskRef(evidenceRef));

            JsonNode result = indawoClient.submitSiteRegistration(req);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("submitted", true);
            data.put("caseRef", text(result, "caseRef"));
            data.put("status", text(result, "status"));
            data.put("note", "Your site registration was received and is under review. It is not listed "
                    + "or usable until the registry confirms it.");
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(envelope(data, requestId, correlationId));
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    @GetMapping("/registrations")
    public ResponseEntity<Map<String, Object>> registrationCases(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(value = "outcome", defaultValue = "DUPLICATE_REVIEW") String outcome) {
        try {
            JsonNode result = indawoClient.listSiteRegistrations(outcome);
            return ok(Map.of("cases", array(result, "cases")), requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Operator grants (SJ1) ──────────────────────────────────────────────

    @PostMapping("/{siteId}/operator-grants")
    public ResponseEntity<Map<String, Object>> requestOperatorGrant(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @PathVariable String siteId,
            @RequestBody Map<String, Object> body) {

        String role = str(body.get("role"));
        if (role == null || !OPERATOR_ROLES.contains(role.toUpperCase(Locale.ROOT))) {
            return error(HttpStatus.BAD_REQUEST, "SITE_ROLE_REQUIRED",
                    "A valid operator role is required (SITE_VIEWER, SITE_OPERATOR, "
                            + "SITE_ADMINISTRATOR, REGULATORY_LIAISON).", requestId, correlationId);
        }
        String evidenceRef = str(body.get("evidenceRef"));
        if (evidenceRef == null) {
            return error(HttpStatus.BAD_REQUEST, "EVIDENCE_REQUIRED",
                    "A supporting evidence reference is required to request operator access.",
                    requestId, correlationId);
        }
        if (submitAssuranceRank() < MIN_SUBMIT_ASSURANCE_RANK) {
            return identityProofingRequired(requestId, correlationId);
        }
        try {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("personHealthId", actorId); // applicant is ALWAYS the session identity
            req.put("role", role.toUpperCase(Locale.ROOT));
            String claimType = str(body.get("claimType"));
            req.put("claimType", claimType != null ? claimType.toUpperCase(Locale.ROOT) : "NEW");
            req.put("evidenceRef", maskRef(evidenceRef));
            if (body.get("validFrom") != null) req.put("validFrom", body.get("validFrom"));
            if (body.get("validTo") != null) req.put("validTo", body.get("validTo"));
            copyIfPresent(body, req, "notes");

            JsonNode result = indawoClient.submitOperatorGrant(siteId, req);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("submitted", true);
            data.put("grantId", text(result, "id"));
            data.put("siteId", text(result, "siteId"));
            data.put("role", text(result, "role"));
            data.put("approvalState", text(result, "approvalState"));
            data.put("note", "Your operator-access request was recorded and is pending approval. "
                    + "No operator capability is active until it is approved.");
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(data, requestId, correlationId));
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    @GetMapping("/operator-grants")
    public ResponseEntity<Map<String, Object>> operatorGrants(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(value = "state", defaultValue = "PENDING") String state) {
        try {
            JsonNode result = indawoClient.listOperatorGrants(state);
            return ok(Map.of("grants", array(result, "grants")), requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    @PostMapping("/operator-grants/{grantId}/approve")
    public ResponseEntity<Map<String, Object>> approveOperatorGrant(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String grantId) {
        try {
            JsonNode result = indawoClient.approveOperatorGrant(grantId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("grantId", text(result, "id"));
            data.put("siteId", text(result, "siteId"));
            data.put("approvalState", text(result, "approvalState"));
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
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
                "Confirm your personal identity before registering a site or requesting operator "
                        + "access. Complete identity verification, then try again.",
                requestId, correlationId);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private List<JsonNode> array(JsonNode result, String field) {
        JsonNode arr = result == null ? null : result.path(field);
        List<JsonNode> out = new java.util.ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(out::add);
        } else if (result != null && result.isArray()) {
            result.forEach(out::add);
        }
        return out;
    }

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
