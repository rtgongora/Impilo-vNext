package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.OrgRegistryFacilityAdminClient;
import zw.gov.mohcc.impilo.experience.client.TusoFacilityClaimClient;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facility administrator claim / appointment BFF glue (IATG Wave 2, WS-E) — citizen-session-aware.
 *
 * <p>Composition layer only, mirroring the Wave-1 WS-F provider-claim conventions EXACTLY: the
 * person is signed in with their Health ID (X-Actor-ID); every claim binds to that session identity
 * — the body can never redirect a claim onto another person. Truth lives in TUSO (facility +
 * appointment SoR); org-registry records the resulting administering relationship.</p>
 *
 * <p>Key differences from WS-F (facility subject, not an identity anchor):</p>
 * <ul>
 *   <li><b>Subject key is the facility UUID</b>, not a Health ID.</li>
 *   <li><b>Eligibility is gated on platform-legitimacy</b> (TUSO consults FacilitySourceLegitimacy):
 *       a facility not allowed on the platform is not claimable. "Already administered" never blocks
 *       — a facility may have many administrators over time.</li>
 *   <li><b>No recover-not-reissue analogue</b>: the sink is an appointment/affiliation record, never
 *       a rebind of a single anchor.</li>
 * </ul>
 *
 * <p><b>Masked at the edge</b>: facility codes and any echoed Health ID return masked to the shell;
 * raw evidence refs are masked before leaving the BFF. Supporting-document evidence is stored in the
 * document service and referenced by opaque object id; organisation-invitation onboarding accepts an
 * invitation token and establishes the affiliation in org-registry.</p>
 */
@RestController
@RequestMapping("/api/v1/facility-claim")
public class FacilityClaimController {

    private static final Logger log = LoggerFactory.getLogger(FacilityClaimController.class);

    static final String EVIDENCE_APPOINTMENT_LETTER = "APPOINTMENT_LETTER";
    static final String EVIDENCE_DOCUMENT = "DOCUMENT";

    private static final long MAX_EVIDENCE_BYTES = 10L * 1024 * 1024; // 10 MB

    private final TusoFacilityClaimClient tusoClient;
    private final OrgRegistryFacilityAdminClient orgRegistryClient;
    private final DocumentServiceClient documentServiceClient;
    private final zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient assuranceClient;

    /**
     * Minimum person-assurance to submit a facility-administrator claim (Place
     * Journey Doctrine §2/D-L4): proof-of-authority has a person half — the
     * claimant must have proven their own identity to ≥LOA2 (consumed from the
     * Wave-G person-proofing spine) — and a document/appointment-letter authority
     * half (the evidenceRef). Knowing the facility's details is never enough.
     */
    private static final int MIN_CLAIM_ASSURANCE_RANK = 2; // LOA2

    public FacilityClaimController(TusoFacilityClaimClient tusoClient,
                                   OrgRegistryFacilityAdminClient orgRegistryClient,
                                   DocumentServiceClient documentServiceClient,
                                   zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient assuranceClient) {
        this.tusoClient = tusoClient;
        this.orgRegistryClient = orgRegistryClient;
        this.documentServiceClient = documentServiceClient;
        this.assuranceClient = assuranceClient;
    }

    /** The session actor's current identity-assurance level; fail-closed to LOA1 on outage. */
    private int claimantAssuranceRank() {
        try {
            JsonNode status = assuranceClient.getAssuranceStatus();
            String level = text(status, "currentLevel");
            return switch (level == null ? "LOA1" : level.trim().toUpperCase(java.util.Locale.ROOT)) {
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

    // ── Eligibility ───────────────────────────────────────────────────────────

    /** Can this facility be claimed/administered by the session person right now? */
    @GetMapping("/eligibility")
    public ResponseEntity<Map<String, Object>> eligibility(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam("facilityUuid") String facilityUuid) {

        if (facilityUuid == null || facilityUuid.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "FACILITY_UUID_REQUIRED",
                    "A facility UUID is required to check claim eligibility.", requestId, correlationId);
        }
        try {
            JsonNode e = tusoClient.eligibility(facilityUuid);
            boolean allowed = e != null && e.path("allowedOnPlatform").asBoolean(false);
            boolean claimable = e != null && e.path("claimable").asBoolean(false);
            boolean alreadyAdministered = e != null && e.path("alreadyAdministered").asBoolean(false);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("facilityUuid", facilityUuid);
            data.put("allowedOnPlatform", allowed);
            data.put("claimable", claimable);
            data.put("alreadyAdministered", alreadyAdministered);
            data.put("activeAdministratorCount",
                    e != null ? e.path("activeAdministratorCount").asInt(0) : 0);
            data.put("paths", List.of(
                    Map.of("path", "APPOINTMENT_LETTER", "available", claimable),
                    Map.of("path", "DOCUMENT", "available", false),
                    Map.of("path", "ORG_INVITATION", "available", false)));
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            return propagate(ex, requestId, correlationId);
        }
    }

    /**
     * Facility-admin appointments for a facility (Facility Mode card 5: pending
     * claims/requests + who currently administers it). The UI derives whether the
     * signed-in user administers THIS facility from these rows — no one administers
     * a facility they hold no active appointment for.
     */
    @GetMapping("/appointments")
    public ResponseEntity<Map<String, Object>> appointments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam("facilityUuid") String facilityUuid) {
        if (facilityUuid == null || facilityUuid.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "FACILITY_UUID_REQUIRED",
                    "A facility UUID is required to list facility-admin appointments.", requestId, correlationId);
        }
        try {
            // ANTI-ENUM (Place Journey Doctrine §2, W4): the administrator roster
            // is disclosed ONLY to someone who already holds an ACTIVE appointment
            // at this facility; everyone else sees just their OWN rows. A facility
            // UUID must never enumerate who runs the place.
            JsonNode all = tusoClient.appointments(facilityUuid);
            List<JsonNode> visible = new java.util.ArrayList<>();
            boolean actorIsActiveAdmin = false;
            if (all != null && all.isArray()) {
                for (JsonNode a : all) {
                    if (actorId != null && actorId.equalsIgnoreCase(text(a, "personHealthId"))
                            && "ACTIVE".equalsIgnoreCase(text(a, "approvalState"))) {
                        actorIsActiveAdmin = true;
                        break;
                    }
                }
                for (JsonNode a : all) {
                    if (actorIsActiveAdmin
                            || (actorId != null && actorId.equalsIgnoreCase(text(a, "personHealthId")))) {
                        visible.add(a);
                    }
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("facilityUuid", facilityUuid);
            data.put("appointments", visible);
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException ex) {
            return propagate(ex, requestId, correlationId);
        }
    }

    // ── Preview (masked) ───────────────────────────────────────────────────────

    /** Masked facility summary shown before an administrator commits to a claim. */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {

        String facilityUuid = str(body.get("facilityUuid"));
        if (facilityUuid == null) {
            return error(HttpStatus.BAD_REQUEST, "FACILITY_UUID_REQUIRED",
                    "A facility UUID is required to preview a facility.", requestId, correlationId);
        }
        try {
            JsonNode p = tusoClient.preview(facilityUuid);
            if (p == null || p.isNull()) {
                return facilityNotRecognised(requestId, correlationId);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("facilityUuid", facilityUuid);
            data.put("facilityCode", maskCode(text(p, "facilityCode")));
            data.put("name", text(p, "name"));
            data.put("facilityType", text(p, "facilityType"));
            data.put("province", text(p, "province"));
            data.put("district", text(p, "district"));
            data.put("allowedOnPlatform", p.path("allowedOnPlatform").asBoolean(false));
            data.put("claimable", p.path("claimable").asBoolean(false));
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 404) {
                return facilityNotRecognised(requestId, correlationId);
            }
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Appoint (claim) ──────────────────────────────────────────────────────

    /**
     * Submit a facility-administrator claim onto the session Health ID. The claimant is ALWAYS
     * X-Actor-ID — never taken from the body. Consent is required. 409s (not claimable / already an
     * administrator) propagate honestly.
     */
    @PostMapping("/appoint")
    public ResponseEntity<Map<String, Object>> appoint(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {

        String facilityUuid = str(body.get("facilityUuid"));
        if (facilityUuid == null) {
            return error(HttpStatus.BAD_REQUEST, "FACILITY_UUID_REQUIRED",
                    "A facility UUID is required to claim a facility.", requestId, correlationId);
        }
        if (!Boolean.TRUE.equals(body.get("consent"))) {
            return error(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED",
                    "Explicit consent is required before requesting facility administrator access.",
                    requestId, correlationId);
        }
        // Person-proofing gate (Wave-G consume, D-L4 person half): a facility
        // claim requires the claimant to have proven their own identity.
        if (claimantAssuranceRank() < MIN_CLAIM_ASSURANCE_RANK) {
            return error(HttpStatus.FORBIDDEN, "IDENTITY_PROOFING_REQUIRED",
                    "Confirm your personal identity before requesting facility administrator access. "
                            + "Complete identity verification, then try again.",
                    requestId, correlationId);
        }
        try {
            Map<String, Object> claim = new LinkedHashMap<>();
            // The claimant is ALWAYS the session Health ID — never taken from the body.
            claim.put("personHealthId", actorId);
            if (str(body.get("role")) != null) {
                claim.put("role", str(body.get("role")));
            }
            // Raw evidence refs never travel downstream unmasked — masked at the edge.
            String evidenceRef = str(body.get("evidenceRef"));
            if (evidenceRef != null) {
                claim.put("evidenceRef", maskRef(evidenceRef));
            }
            JsonNode result = tusoClient.submitClaim(facilityUuid, claim);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("submitted", true);
            data.put("appointmentId", text(result, "id"));
            data.put("facilityUuid", facilityUuid);
            data.put("personHealthId", maskId(text(result, "personHealthId")));
            data.put("role", text(result, "role"));
            data.put("approvalState", text(result, "approvalState"));
            data.put("note", "Your facility administrator request was recorded and is pending approval. "
                    + "No administrative capability is active until it is approved.");
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(data, requestId, correlationId));
        } catch (HttpStatusCodeException e) {
            // 409 (facility not allowed on platform / already an administrator) and every other
            // downstream verdict propagate honestly.
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Approve → ACTIVE + affiliation ────────────────────────────────────────

    /**
     * Approve a PENDING appointment → ACTIVE (TUSO), then record the administering affiliation in
     * org-registry when an administering organisation is known. The affiliation is a relationship
     * link only; TUSO stays the facility and appointment SoR.
     */
    @PostMapping("/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {

        String appointmentId = str(body.get("appointmentId"));
        if (appointmentId == null) {
            return error(HttpStatus.BAD_REQUEST, "APPOINTMENT_ID_REQUIRED",
                    "An appointment id is required to approve a facility administrator.", requestId, correlationId);
        }
        try {
            JsonNode approved = tusoClient.approve(appointmentId, Map.of("approvedBy", actorId));
            String facilityUuid = text(approved, "facilityUuid");
            String organizationId = str(body.get("organizationId"));

            boolean affiliationRecorded = false;
            if (organizationId != null && facilityUuid != null) {
                Map<String, Object> affiliation = new LinkedHashMap<>();
                affiliation.put("facilityUuid", facilityUuid);
                affiliation.put("personHealthId", text(approved, "personHealthId"));
                affiliation.put("appointedBy", actorId);
                orgRegistryClient.recordFacilityAdminAffiliation(organizationId, affiliation);
                affiliationRecorded = true;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("appointmentId", text(approved, "id"));
            data.put("facilityUuid", facilityUuid);
            data.put("personHealthId", maskId(text(approved, "personHealthId")));
            data.put("approvalState", text(approved, "approvalState"));
            data.put("affiliationRecorded", affiliationRecorded);
            if (!affiliationRecorded) {
                data.put("affiliationNote", "No administering organisation supplied — the appointment is "
                        + "active but no org-registry affiliation was recorded.");
            }
            return ok(data, requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Evidence (document upload) ────────────────────────────────────────────

    /**
     * Upload a supporting document as facility-claim evidence. The file is stored in the document
     * service (real object store + scan pipeline); the returned {@code evidenceRef} (object id) is
     * then supplied on the {@code /appoint} call as the claim's evidence. The document owner is the
     * session Health ID. Downstream document-service verdicts propagate honestly.
     */
    @PostMapping(value = "/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> evidence(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestPart("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "EVIDENCE_FILE_REQUIRED",
                    "A document file is required for document-based evidence.", requestId, correlationId);
        }
        if (file.getSize() > MAX_EVIDENCE_BYTES) {
            return error(HttpStatus.PAYLOAD_TOO_LARGE, "EVIDENCE_FILE_TOO_LARGE",
                    "The supporting document must be 10 MB or smaller.", requestId, correlationId);
        }
        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        try {
            JsonNode stored = documentServiceClient.uploadObject(
                    file.getBytes(),
                    file.getOriginalFilename(),
                    mimeType,
                    Map.of("purpose", "facility-claim-evidence", "uploadedBy", actorId));
            Map<String, Object> data = new LinkedHashMap<>();
            // The object id is an opaque handle used as the claim evidence reference on /appoint.
            data.put("evidenceRef", text(stored, "objectId"));
            data.put("filename", text(stored, "originalFilename"));
            data.put("mimeType", text(stored, "mimeType"));
            data.put("scanStatus", text(stored, "scanStatus"));
            data.put("note", "Your document was uploaded. Continue to preview the facility and submit "
                    + "your administrator request with this evidence attached.");
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(data, requestId, correlationId));
        } catch (IOException e) {
            return error(HttpStatus.BAD_REQUEST, "EVIDENCE_FILE_UNREADABLE",
                    "The supporting document could not be read.", requestId, correlationId);
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Org invitation (Channel C onboarding) ─────────────────────────────────

    /**
     * Accept an organisation invitation to administer a facility. The invitee supplies the
     * single-use token from their invitation; org-registry validates it and establishes the
     * affiliation for the session Health ID (never taken from the body). Unknown/expired/consumed
     * tokens propagate honestly (404/409).
     */
    @PostMapping("/org-invitation")
    public ResponseEntity<Map<String, Object>> orgInvitation(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestBody Map<String, Object> body) {

        String token = str(body.get("token"));
        if (token == null || token.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "INVITATION_TOKEN_REQUIRED",
                    "An invitation token is required to accept an organisation invitation.",
                    requestId, correlationId);
        }
        try {
            // The accepting person is ALWAYS the session Health ID — never taken from the body.
            JsonNode result = orgRegistryClient.acceptInvitation(token, actorId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("accepted", true);
            data.put("invitationId", text(result, "id"));
            data.put("organizationId", text(result, "organizationId"));
            data.put("role", text(result, "role"));
            data.put("affiliationId", text(result, "affiliationId"));
            data.put("status", text(result, "status"));
            data.put("note", "Your organisation invitation was accepted and your administering "
                    + "affiliation is now active.");
            return ResponseEntity.status(HttpStatus.CREATED).body(envelope(data, requestId, correlationId));
        } catch (HttpStatusCodeException e) {
            return propagate(e, requestId, correlationId);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> facilityNotRecognised(String requestId, String correlationId) {
        return error(HttpStatus.NOT_FOUND, "FACILITY_NOT_RECOGNISED",
                "That facility is not recognised. Check the facility UUID, or contact the facility registry.",
                requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> ok(
            Map<String, Object> data, String requestId, String correlationId) {
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

    /** Propagate a downstream verdict (status + message) honestly — never remap 409s. */
    private ResponseEntity<Map<String, Object>> propagate(
            HttpStatusCodeException e, String requestId, String correlationId) {
        String message = e.getResponseBodyAsString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "error", Map.of(
                        "code", "UPSTREAM_" + e.getStatusCode().value(),
                        "message", message != null && !message.isBlank() ? message : e.getStatusText()),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    /** Mask a Health ID / long identifier: first 4 + *** + last 2. */
    static String maskId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String v = id.trim();
        if (v.length() <= 6) {
            return v.charAt(0) + "***";
        }
        return v.substring(0, 4) + "***" + v.substring(v.length() - 2);
    }

    /** Mask a facility code: first 4 + ***. */
    static String maskCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String v = code.trim();
        return (v.length() <= 4 ? v : v.substring(0, 4)) + "***";
    }

    /** Mask an evidence reference: first 2 + ***. */
    static String maskRef(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        return (v.length() <= 2 ? v : v.substring(0, 2)) + "***";
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String str(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
