package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineBillingContextService;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineGovernanceService;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineOperatingModelRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Citizen virtual-care lane (Lane E Wave 1): discover the governed virtual-hospital
 * directory with HONEST availability, and request a teleconsult that lands as a real,
 * pool-ROUTED PCT referral on the existing teleconsult spine (DRAFT → routing persisted →
 * SUBMITTED → visible on the virtual hospital's specialty-pool queue for provider accept).
 *
 * <p>Honesty rules: only virtual hospitals whose substrate is ROUTABLE_VIA_EXISTING_SEAMS
 * with a resolvable SPECIALTY_POOL target are REQUESTABLE; everything else is COMING_SOON
 * with no call-to-action. Citizen endpoints are strictly self-scoped server-side: the
 * patient anchor is always the caller's {@code X-Actor-ID}; no body/query patient override
 * is honoured.</p>
 */
@RestController
@RequestMapping("/internal/v1/virtual-care")
public class VirtualCareController {

    private static final Logger log = LoggerFactory.getLogger(VirtualCareController.class);

    static final String ROUTABLE_SUBSTRATE = "ROUTABLE_VIA_EXISTING_SEAMS";
    private static final Set<String> ALLOWED_MODALITIES = Set.of("VIDEO", "AUDIO", "MESSAGE");
    private static final Set<String> MEDIA_MODALITIES = Set.of("VIDEO", "AUDIO");

    private final TelemedicineOperatingModelRegistry registry;
    private final PctServiceClient pctClient;
    private final VitoServiceClient vitoClient;
    private final MvumoServiceClient mvumoClient;
    private final NotificationServiceClient notificationClient;
    private final TelemedicineBillingContextService billingContextService;
    private final TelemedicineGovernanceService governanceService;

    public VirtualCareController(TelemedicineOperatingModelRegistry registry,
                                 PctServiceClient pctClient,
                                 VitoServiceClient vitoClient,
                                 MvumoServiceClient mvumoClient,
                                 NotificationServiceClient notificationClient,
                                 TelemedicineBillingContextService billingContextService,
                                 TelemedicineGovernanceService governanceService) {
        this.registry = registry;
        this.pctClient = pctClient;
        this.vitoClient = vitoClient;
        this.mvumoClient = mvumoClient;
        this.notificationClient = notificationClient;
        this.billingContextService = billingContextService;
        this.governanceService = governanceService;
    }

    /**
     * Citizen-safe virtual-hospital directory with honest availability: REQUESTABLE only
     * when the VH is routable over an existing SPECIALTY_POOL seam; otherwise COMING_SOON.
     * Internal operating-model fields (substrate/seam/queue plumbing) are not exposed.
     */
    @GetMapping("/virtual-hospitals")
    public ResponseEntity<Map<String, Object>> listVirtualHospitals(
            @RequestParam(defaultValue = "citizen") String audience,
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId) {
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Map<String, Object> hospital : registry.virtualHospitals()) {
            cards.add(toCitizenCard(hospital));
        }
        return ok(Map.of("audience", audience, "virtualHospitals", cards), requestId, correlationId, HttpStatus.OK);
    }

    /**
     * Citizen teleconsult request into a virtual hospital's specialty pool. Fail-closed:
     * identity is verified against VITO before any referral exists; media modalities
     * (video/audio) require consent; a non-routable VH is an honest 422 with no side
     * effects. The patient anchor is ALWAYS the caller ({@code X-Actor-ID}).
     */
    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> createRequest(
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        if (actorId == null || actorId.isBlank()) {
            return error(HttpStatus.UNAUTHORIZED, "MISSING_ACTOR_IDENTITY",
                    "A citizen identity (X-Actor-ID) is required to request virtual care",
                    requestId, correlationId);
        }
        String normalizedPurpose;
        try {
            normalizedPurpose = governanceService.normalizePurposeOfUse(purposeOfUse);
        } catch (ResponseStatusException e) {
            HttpStatus resolved = HttpStatus.resolve(e.getStatusCode().value());
            return error(resolved == null ? HttpStatus.BAD_REQUEST : resolved, "INVALID_PURPOSE_OF_USE",
                    e.getReason() == null ? "Unsupported purpose of use" : e.getReason(),
                    requestId, correlationId);
        }

        String virtualHospitalId = val(body, "virtualHospitalId", "virtual_hospital_id");
        if (virtualHospitalId == null || virtualHospitalId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "MISSING_VIRTUAL_HOSPITAL",
                    "virtualHospitalId is required", requestId, correlationId);
        }
        Map<String, Object> hospital = registry.virtualHospital(virtualHospitalId.trim()).orElse(null);
        if (hospital == null) {
            return error(HttpStatus.NOT_FOUND, "VIRTUAL_HOSPITAL_UNKNOWN",
                    "No virtual hospital configured with id: " + virtualHospitalId, requestId, correlationId);
        }
        String poolId = resolvePoolId(hospital);
        if (poolId == null) {
            // Honest availability: configured but not yet routable — no referral is created.
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "VIRTUAL_HOSPITAL_NOT_YET_ROUTABLE",
                    hospitalName(hospital) + " is configured but not yet reachable for requests. "
                            + "It will open once its routing substrate is live.",
                    requestId, correlationId);
        }

        String reason = val(body, "reason", "clinicalQuestion", "clinical_question");
        if (reason == null || reason.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "MISSING_REASON",
                    "reason is required to request a teleconsult", requestId, correlationId);
        }
        String modality = defaultString(val(body, "modality", "virtualMode", "virtual_mode"), "video")
                .trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_MODALITIES.contains(modality)) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_MODALITY",
                    "modality must be one of video, audio, message", requestId, correlationId);
        }
        boolean consentGranted = Boolean.parseBoolean(val(body, "consentGranted", "consent_granted"));
        if (MEDIA_MODALITIES.contains(modality) && !consentGranted) {
            // Consent gate: governed telemedicine media requires captured consent up front.
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "CONSENT_REQUIRED",
                    "Consent must be granted before requesting a video or audio teleconsult",
                    requestId, correlationId);
        }

        // VITO fail-closed identity check (dual lookup: client-registry profile, then the
        // legacy health-id entity) — never create clinical referrals against an
        // unverifiable identity.
        IdentityCheck identity = verifyCitizenIdentity(actorId.trim());
        if (identity == IdentityCheck.UPSTREAM_ERROR) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "VITO_UNAVAILABLE",
                    "The patient registry is temporarily unavailable. Please try again shortly.",
                    requestId, correlationId);
        }
        if (identity == IdentityCheck.NOT_FOUND) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "PATIENT_NOT_FOUND",
                    "Your identity could not be verified in the patient registry.",
                    requestId, correlationId);
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("patient_id", actorId.trim());
            payload.put("urgency", defaultString(val(body, "urgency"), "ROUTINE"));
            payload.put("specialty", specialtyFor(hospital, poolId));
            payload.put("clinical_question", reason.trim());
            payload.put("modality", "virtual");
            payload.put("virtual_mode", modality.toLowerCase(Locale.ROOT));
            payload.put("routing_kind", "POOL");
            payload.put("routing_pool_id", poolId);
            payload.put("consent_required", true);
            payload.put("purpose_of_use", normalizedPurpose);
            billingContextService.applyBillingContext(payload, actorId.trim(), facilityId, body);

            JsonNode created = pctClient.createReferral(payload);
            if (created == null || !created.hasNonNull("id")) {
                return upstreamFailure("PCT_UNAVAILABLE", "No referral payload returned", requestId, correlationId);
            }
            String referralId = created.get("id").asText();

            if (MEDIA_MODALITIES.contains(modality)) {
                // Attach the governed consent record before the request goes live; a consent
                // plane outage fails closed (referral stays DRAFT, request is NOT queued).
                ResponseEntity<Map<String, Object>> consentFailure =
                        recordConsent(referralId, actorId.trim(), modality, requestId, correlationId);
                if (consentFailure != null) {
                    return consentFailure;
                }
            }

            JsonNode submitted = pctClient.submitReferral(referralId);
            if (submitted == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No referral submit payload returned", requestId, correlationId);
            }

            governanceService.audit(
                    tenantId, correlationId, normalizedPurpose, facilityId,
                    "TELECONSULT_CITIZEN_POOL_REQUESTED", "POST:virtual-care/requests", "SUCCESS",
                    actorId, "PATIENT", actorId, "TeleconsultReferral", referralId,
                    Map.of("virtualHospitalId", virtualHospitalId.trim(),
                            "poolId", poolId,
                            "modality", modality));
            notifyQueued(actorId.trim(), referralId, hospitalName(hospital));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestId", referralId);
            data.put("status", submitted.path("status").asText("SUBMITTED"));
            data.put("virtualHospital", Map.of("id", virtualHospitalId.trim(), "name", hospitalName(hospital)));
            data.put("poolId", poolId);
            data.put("modality", modality.toLowerCase(Locale.ROOT));
            data.put("message", "Queued for " + hospitalName(hospital) + " — a provider will accept your request.");
            return ok(data, requestId, correlationId, HttpStatus.CREATED);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * The caller's own virtual-care requests — strictly self-scoped: the patient anchor is
     * the {@code X-Actor-ID}; no patient query parameter exists on this endpoint.
     */
    @GetMapping("/requests/mine")
    public ResponseEntity<Map<String, Object>> myRequests(
            @RequestHeader(value = CompanionHeaders.REQUEST_ID, required = false) String requestId,
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (actorId == null || actorId.isBlank()) {
            return error(HttpStatus.UNAUTHORIZED, "MISSING_ACTOR_IDENTITY",
                    "A citizen identity (X-Actor-ID) is required to list virtual care requests",
                    requestId, correlationId);
        }
        try {
            JsonNode referrals = pctClient.listPatientReferrals(actorId.trim(), page, Math.min(Math.max(size, 1), 100));
            List<Map<String, Object>> rows = new ArrayList<>();
            if (referrals != null && referrals.isArray()) {
                for (JsonNode referral : referrals) {
                    if (!"virtual".equalsIgnoreCase(referral.path("modality").asText(""))) {
                        continue;
                    }
                    rows.add(toCitizenRequestRow(referral));
                }
            }
            return ok(Map.of("requests", rows), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    // ── mapping helpers ─────────────────────────────────────────────────────

    private Map<String, Object> toCitizenCard(Map<String, Object> hospital) {
        String poolId = resolvePoolId(hospital);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", hospital.get("id"));
        card.put("name", hospital.get("name"));
        card.put("level", hospital.get("level"));
        card.put("purpose", hospital.get("purpose"));
        card.put("catchment", hospital.get("catchment"));
        card.put("serviceLines", hospital.getOrDefault("serviceLines", List.of()));
        card.put("availability", poolId != null ? "REQUESTABLE" : "COMING_SOON");
        if (poolId != null) {
            card.put("poolId", poolId);
        }
        return card;
    }

    /** Resolve the SPECIALTY_POOL routing target for a ROUTABLE virtual hospital, else null. */
    static String resolvePoolId(Map<String, Object> hospital) {
        if (!ROUTABLE_SUBSTRATE.equals(hospital.get("substrateStatus"))) {
            return null;
        }
        Object rawSeams = hospital.get("currentRoutingSeams");
        if (!(rawSeams instanceof List<?> seams) || seams.isEmpty()) {
            return null;
        }
        for (Object rawSeam : seams) {
            if (!(rawSeam instanceof Map<?, ?> seam)) continue;
            Object type = seam.get("routingType");
            Object target = seam.get("targetRef");
            if ("SPECIALTY_POOL".equals(type) && target instanceof String ref && !ref.isBlank()) {
                return ref.trim();
            }
        }
        return null;
    }

    /** Specialty for the referral: the VH's primary service line, normalized; pool key as fallback. */
    private String specialtyFor(Map<String, Object> hospital, String poolId) {
        Object rawLines = hospital.get("serviceLines");
        if (rawLines instanceof List<?> lines && !lines.isEmpty() && lines.get(0) instanceof String first
                && !first.isBlank()) {
            return first.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        }
        return poolId;
    }

    private String hospitalName(Map<String, Object> hospital) {
        Object name = hospital.get("name");
        return name == null ? "the virtual hospital" : name.toString();
    }

    private Map<String, Object> toCitizenRequestRow(JsonNode referral) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", referral.path("id").asText(null));
        row.put("status", referral.path("status").asText(null));
        row.put("specialty", referral.path("specialty").asText(null));
        row.put("reason", referral.path("reason").asText(null));
        row.put("modality", referral.path("virtualMode").asText(null));
        String poolId = referral.path("routingPoolId").asText(null);
        row.put("poolId", poolId);
        row.put("virtualHospital", virtualHospitalForPool(poolId));
        row.put("createdAt", referral.path("createdAt").asText(null));
        row.put("submittedAt", referral.path("submittedAt").asText(null));
        row.put("scheduledAt", referral.path("scheduledAt").asText(null));
        return row;
    }

    private Map<String, Object> virtualHospitalForPool(String poolId) {
        if (poolId == null || poolId.isBlank()) {
            return null;
        }
        for (Map<String, Object> hospital : registry.virtualHospitals()) {
            if (poolId.equals(resolvePoolId(hospital))) {
                return Map.of("id", hospital.get("id"), "name", hospital.get("name"));
            }
        }
        return null;
    }

    private enum IdentityCheck { FOUND, NOT_FOUND, UPSTREAM_ERROR }

    private IdentityCheck verifyCitizenIdentity(String actorId) {
        boolean upstreamErrored = false;
        try {
            JsonNode profile = vitoClient.getClientRegistryProfile(actorId);
            if (profile != null && !profile.isNull()) {
                return IdentityCheck.FOUND;
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            // fall through to the legacy path
        } catch (Exception e) {
            upstreamErrored = true;
        }
        try {
            JsonNode patient = vitoClient.getPatient(actorId);
            if (patient != null && !patient.isNull()) {
                return IdentityCheck.FOUND;
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            // definitively unknown on this path
        } catch (Exception e) {
            upstreamErrored = true;
        }
        return upstreamErrored ? IdentityCheck.UPSTREAM_ERROR : IdentityCheck.NOT_FOUND;
    }

    /**
     * Governed consent record on the mvumo plane (same seam TeleconsultController's
     * /consent uses), written back onto the referral. Returns an error response when the
     * consent plane is unavailable — the media-modality request must NOT proceed.
     */
    private ResponseEntity<Map<String, Object>> recordConsent(String referralId, String actorId,
                                                              String modality, String requestId,
                                                              String correlationId) {
        try {
            Map<String, Object> consentRequest = new LinkedHashMap<>();
            consentRequest.put("subjectPatientRef", actorId);
            consentRequest.put("consentType", "TELECONSULT_MEDIA");
            consentRequest.put("workflowRef", "referral:" + referralId);
            consentRequest.put("context", Map.of("referralId", referralId, "modality", modality));
            JsonNode mvumo = mvumoClient.createConsentRequest(consentRequest);
            String consentId = mvumo != null && mvumo.hasNonNull("id") ? mvumo.get("id").asText() : null;
            String tshepoConsentId = mvumo != null && mvumo.hasNonNull("tshepoConsentId")
                    ? mvumo.get("tshepoConsentId").asText() : null;

            Map<String, Object> pctConsent = new LinkedHashMap<>();
            pctConsent.put("consent_type", "TELECONSULT_MEDIA");
            pctConsent.put("consent_status", "GRANTED");
            pctConsent.put("consent_reference", consentId);
            pctConsent.put("mvumo_session_id", consentId);
            pctConsent.put("tshepo_decision_id", tshepoConsentId);
            pctClient.updateReferralConsent(referralId, pctConsent);
            return null;
        } catch (Exception e) {
            log.warn("Virtual-care consent recording failed for referral {}: {}", referralId, e.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "MVUMO_UNAVAILABLE",
                    "The consent service is temporarily unavailable; your request was not submitted.",
                    requestId, correlationId);
        }
    }

    private void notifyQueued(String recipient, String referralId, String hospitalName) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("templateKey", "TELECONSULT_REQUESTED");
            body.put("channel", "IN_APP");
            body.put("to", recipient);
            body.put("variables", Map.of(
                    "actorId", recipient,
                    "message", "Your teleconsult request is queued for " + hospitalName
                            + ". A provider will accept it shortly.",
                    "sessionId", referralId));
            notificationClient.sendNotification(body);
        } catch (Exception ex) {
            log.warn("Virtual-care request notification failed: {}", ex.getMessage());
        }
    }

    // ── envelope helpers (same shape as TeleconsultController) ─────────────

    private String val(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) return value.toString();
        }
        return null;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private ResponseEntity<Map<String, Object>> ok(Object data, String requestId, String correlationId,
                                                   HttpStatus status) {
        return ResponseEntity.status(status).body(Map.of(
                "data", data,
                "meta", meta(requestId, correlationId)));
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message,
                                                      String requestId, String correlationId) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", meta(requestId, correlationId)));
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(String code, String message,
                                                                String requestId, String correlationId) {
        log.warn("Virtual-care upstream failure: {}", message);
        return error(HttpStatus.BAD_GATEWAY, code,
                message != null ? message : "Virtual-care upstream unavailable", requestId, correlationId);
    }

    private Map<String, Object> meta(String requestId, String correlationId) {
        return Map.of(
                "request_id", requestId != null ? requestId : UUID.randomUUID().toString(),
                "correlation_id", correlationId != null ? correlationId : UUID.randomUUID().toString());
    }
}
