package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.AnalyticsPipelineServiceClient;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.FhirGatewayServiceClient;
import zw.gov.mohcc.impilo.experience.client.KhulumaServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.client.RtcGatewayServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineGovernanceService;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Teleconsultation lifecycle endpoints backed by canonical PCT + MVUMO flows.
 *
 * <p>Message transport endpoints remain fail-closed while real-time channel
 * infrastructure is not part of the canonical stack.</p>
 */
@RestController
@RequestMapping("/internal/v1/teleconsult")
public class TeleconsultController {

    private static final Logger log = LoggerFactory.getLogger(TeleconsultController.class);
    private static final Set<String> UNSUPPORTED_ROUTING_TYPES = Set.of("POOL", "NATIONAL_POOL");
    private static final Set<String> TEAM_ROUTING_TYPES = Set.of("TEAM", "SPECIALTY_POOL");
    private static final Set<String> PRACTITIONER_ROUTING_TYPES = Set.of("PRACTITIONER", "PROVIDER");
    private static final String ON_CALL_ROUTING_TYPE = "ON_CALL";
    private static final Set<String> ALLOWED_PARTICIPANT_ROLES =
            Set.of("PROVIDER", "PATIENT", "CAREGIVER", "INTERPRETER", "SUPERVISOR", "OBSERVER");
    private static final Set<String> ALLOWED_MEDIA_PROFILES = Set.of("FULL", "AUDIO_ONLY");
    /** Dedupe window for the provider-facing patient-waiting notification (per session+identity). */
    private static final long WAITING_NOTIFICATION_TTL_MS = 5 * 60 * 1000L;

    private final Map<String, Long> waitingNotificationSentAt = new java.util.concurrent.ConcurrentHashMap<>();

    private final PctServiceClient pctClient;
    private final MvumoServiceClient mvumoClient;
    private final DocumentServiceClient documentClient;
    private final VarapiServiceClient varapiClient;
    private final TusoServiceClient tusoClient;
    private final CoverageServiceClient coverageClient;
    private final NotificationServiceClient notificationClient;
    private final FhirGatewayServiceClient fhirGatewayClient;
    private final CostaServiceClient costaClient;
    private final AnalyticsPipelineServiceClient analyticsClient;
    private final RtcGatewayServiceClient rtcClient;
    private final TelemedicineGovernanceService telemedicineGovernanceService;
    private final VitoServiceClient vitoClient;
    private final BookingServiceClient bookingClient;
    private final KhulumaServiceClient khulumaClient;
    private final ObjectMapper objectMapper;

    public TeleconsultController(PctServiceClient pctClient,
                                 VitoServiceClient vitoClient,
                                 MvumoServiceClient mvumoClient,
                                 DocumentServiceClient documentClient,
                                 VarapiServiceClient varapiClient,
                                 TusoServiceClient tusoClient,
                                 CoverageServiceClient coverageClient,
                                 NotificationServiceClient notificationClient,
                                 FhirGatewayServiceClient fhirGatewayClient,
                                 CostaServiceClient costaClient,
                                 AnalyticsPipelineServiceClient analyticsClient,
                                 RtcGatewayServiceClient rtcClient,
                                 BookingServiceClient bookingClient,
                                 KhulumaServiceClient khulumaClient,
                                 TelemedicineGovernanceService telemedicineGovernanceService,
                                 ObjectMapper objectMapper) {
        this.pctClient = pctClient;
        this.vitoClient = vitoClient;
        this.mvumoClient = mvumoClient;
        this.documentClient = documentClient;
        this.varapiClient = varapiClient;
        this.tusoClient = tusoClient;
        this.coverageClient = coverageClient;
        this.notificationClient = notificationClient;
        this.fhirGatewayClient = fhirGatewayClient;
        this.costaClient = costaClient;
        this.analyticsClient = analyticsClient;
        this.rtcClient = rtcClient;
        this.bookingClient = bookingClient;
        this.khulumaClient = khulumaClient;
        this.telemedicineGovernanceService = telemedicineGovernanceService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            String normalizedPurpose = telemedicineGovernanceService.normalizePurposeOfUse(purposeOfUse);
            // Teleconsult requests must reference a real person anchor — validate the
            // patient against VITO (the identity SoR) before creating any referral.
            String requestedPatientId = val(body, "patientId", "patient_id");
            if (requestedPatientId != null && !requestedPatientId.isBlank()) {
                // Mirror PatientController's dual lookup: client-registry profile
                // (CPID) first, legacy health-id entity second.
                String lookupId = requestedPatientId.trim();
                boolean found = false;
                boolean upstreamErrored = false;
                try {
                    com.fasterxml.jackson.databind.JsonNode profile = vitoClient.getClientRegistryProfile(lookupId);
                    found = profile != null && !profile.isNull();
                } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
                    // fall through to the legacy path
                } catch (Exception e) {
                    upstreamErrored = true;
                }
                if (!found) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode patient = vitoClient.getPatient(lookupId);
                        found = patient != null && !patient.isNull();
                    } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
                        // definitively unknown on this path
                    } catch (Exception e) {
                        upstreamErrored = true;
                    }
                }
                if (!found && upstreamErrored) {
                    // Fail closed: never create clinical referrals against an unverifiable identity.
                    return ResponseEntity.status(503).body(Map.of(
                            "error", Map.of("code", "VITO_UNAVAILABLE",
                                    "message", "The patient registry is temporarily unavailable. Please try again shortly."),
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
                if (!found) {
                    return ResponseEntity.unprocessableEntity().body(Map.of(
                            "error", Map.of("code", "PATIENT_NOT_FOUND",
                                    "message", "The referenced patient does not exist in the patient registry."),
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("encounter_id", val(body, "encounterId", "encounter_id"));
            payload.put("patient_id", val(body, "patientId", "patient_id"));
            payload.put("provider_id", actorId != null ? actorId : val(body, "providerId", "provider_id"));
            payload.put("urgency", val(body, "urgency"));
            payload.put("specialty", val(body, "specialty"));
            payload.put("clinical_question", val(body, "clinicalQuestion", "reason"));
            payload.put("modality", "virtual");
            payload.put("virtual_mode", defaultString(val(body, "virtualMode", "virtual_mode"), "video"));
            payload.put("session_provider", defaultString(
                    val(body, "sessionProvider", "session_provider", "providerType", "provider_type"),
                    "EXTERNAL_MANAGED"));
            payload.put("consent_required", true);
            payload.put("purpose_of_use", normalizedPurpose);
            applyBillingContext(payload, val(body, "patientId", "patient_id"), facilityId, body);
            var created = pctClient.createReferral(payload);
            if (created == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No teleconsult session payload returned", requestId, correlationId);
            }
            emitTelemedicineNotification("TELECONSULT_REQUESTED", created, actorId, "A teleconsult request is waiting for specialist review.");
            JsonNode responsePayload = created;
            String scheduledAt = val(body, "scheduledAt", "scheduled_at");
            if (scheduledAt != null && !scheduledAt.isBlank()) {
                responsePayload = scheduleTeleconsultAppointment(created, scheduledAt.trim(), facilityId, actorId, body);
            }
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, normalizedPurpose, facilityId,
                    "TELEMEDICINE_SESSION_CREATED", "POST:teleconsult/sessions", "SUCCESS",
                    actorId, "PROVIDER", val(body, "patientId", "patient_id"), "TeleconsultSession",
                    extractId(created), Map.of("mode", "virtual", "scheduled", scheduledAt != null && !scheduledAt.isBlank()));
            return ok(responsePayload, requestId, correlationId, HttpStatus.CREATED);
        } catch (ResponseStatusException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            return error(status == null ? HttpStatus.BAD_REQUEST : status,
                    "TELEMEDICINE_GOVERNANCE_INVALID",
                    e.getReason() == null ? "Telemedicine governance rejected request" : e.getReason(),
                    requestId,
                    correlationId);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PutMapping("/sessions/{id}/referral")
    public ResponseEntity<Map<String, Object>> updateReferral(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            List<String> attachmentRefs = extractAttachmentReferences(body.get("attachments"));
            ValidationError attachmentValidation = validateAttachmentReferences(attachmentRefs);
            if (attachmentValidation != null) {
                return attachmentValidation.toResponse(requestId, correlationId);
            }

            String routingType = normalizedRoutingType(val(body, "routingType", "routing_type"));
            String routingTarget = val(body, "routingTarget", "routing_target_ref", "routing_target");
            ValidationError routingValidation = validateRoutingTarget(routingType, routingTarget, body);
            if (routingValidation != null) {
                return routingValidation.toResponse(requestId, correlationId);
            }

            Map<String, Object> update = new LinkedHashMap<>();
            update.put("stage", inferStage(body));
            update.put("referral_letter", val(body, "referralLetter"));
            update.put("patient_summary", val(body, "patientSummary"));
            update.put("visit_summary", val(body, "visitSummary"));
            update.put("clinical_question", val(body, "clinicalQuestion"));
            update.put("attachment_document_ids", attachmentRefs);
            if (routingType != null) {
                update.put("routing_target", Map.of(
                        "type", routingType,
                        "target_ref", routingTarget == null ? "" : routingTarget));
            }
            update.put("preferredMode", val(body, "preferredMode"));
            var updated = pctClient.updateReferralStage(id, update);
            if (updated == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No referral update payload returned", requestId, correlationId);
            }
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_REFERRAL_UPDATED", "PUT:teleconsult/referral", "SUCCESS",
                    actorId, "PROVIDER", val(body, "patientId", "patient_id"), "TeleconsultReferral",
                    id, Map.of("routingType", normalizedRoutingType(val(body, "routingType", "routing_type"))));
            return ok(normalizeReferralPayload(updated), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/consent")
    public ResponseEntity<Map<String, Object>> recordConsent(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            String patientRef = val(body, "patientId", "patient_id");
            if (patientRef == null || patientRef.isBlank()) {
                var referral = pctClient.getReferral(id);
                if (referral != null && referral.get("patientCpid") != null) {
                    patientRef = referral.get("patientCpid").asText();
                }
            }
            Map<String, Object> consentRequest = new LinkedHashMap<>();
            consentRequest.put("subjectPatientRef", patientRef);
            consentRequest.put("consentType", val(body, "type", "consentType"));
            consentRequest.put("workflowRef", "referral:" + id);
            consentRequest.put("encounterRef", val(body, "encounterId", "encounter_id"));
            consentRequest.put("context", Map.of("referralId", id));

            var mvumo = mvumoClient.createConsentRequest(consentRequest);
            String consentId = mvumo != null && mvumo.get("id") != null ? mvumo.get("id").asText() : null;
            String tshepoConsentId = mvumo != null && mvumo.get("tshepoConsentId") != null ? mvumo.get("tshepoConsentId").asText() : null;

            Map<String, Object> pctConsent = new LinkedHashMap<>();
            pctConsent.put("consent_type", val(body, "type", "consentType"));
            pctConsent.put("consent_status", "PENDING");
            pctConsent.put("consent_reference", consentId);
            pctConsent.put("mvumo_session_id", consentId);
            pctConsent.put("tshepo_decision_id", tshepoConsentId);
            pctClient.updateReferralConsent(id, pctConsent);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("consentToken", consentId);
            response.put("consentReference", consentId);
            response.put("mvumoSessionId", consentId);
            response.put("tshepoDecisionId", tshepoConsentId);
            response.put("status", "PENDING");
            return ok(response, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("MVUMO_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/submit")
    public ResponseEntity<Map<String, Object>> submitReferral(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            JsonNode referral = pctClient.getReferral(id);
            ValidationError submitValidation = validateStoredRoutingAndAttachments(referral);
            if (submitValidation != null) {
                return submitValidation.toResponse(requestId, correlationId);
            }
            ResponseEntity<Map<String, Object>> onCallFailure = resolveOnCallRoutingIfNeeded(
                    id, referral, tenantId, correlationId, purposeOfUse, facilityId, actorId, requestId);
            if (onCallFailure != null) {
                return onCallFailure;
            }
            var submitted = pctClient.submitReferral(id);
            if (submitted == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No referral submit payload returned", requestId, correlationId);
            }
            emitTelemedicineNotification("TELECONSULT_SUBMITTED", submitted, actorId, "Teleconsult referral submitted for specialist action.");
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_REFERRAL_SUBMITTED", "POST:teleconsult/submit", "SUCCESS",
                    actorId, "PROVIDER", extractPatient(submitted), "TeleconsultReferral",
                    id, Map.of());
            return ok(normalizeReferralPayload(submitted), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String referrerId,
            @RequestParam(required = false, name = "patient_id") String patientIdAlias,
            @RequestParam(required = false, name = "referrer_id") String referrerIdAlias,
            @RequestParam(required = false, name = "provider_id") String providerIdAlias,
            @RequestParam(required = false, name = "facility_id") String facilityIdAlias,
            @RequestParam(required = false, name = "referral_id") String referralIdAlias,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            JsonNode list;
            int safeSize = Math.min(Math.max(size, 1), 100);
            String resolvedPatientId = firstNonBlank(patientId, patientIdAlias);
            String resolvedReferrerId = firstNonBlank(referrerId, referrerIdAlias, providerIdAlias, facilityIdAlias);
            if (resolvedPatientId != null && !resolvedPatientId.isBlank()) {
                list = pctClient.listPatientReferrals(resolvedPatientId, page, safeSize);
                list = filterReferralsByStatus(list, status);
            } else if (resolvedReferrerId != null && !resolvedReferrerId.isBlank()) {
                list = pctClient.listIncomingReferrals(resolvedReferrerId, status, page, safeSize);
            } else {
                return error(HttpStatus.BAD_REQUEST, "MISSING_FILTER",
                        "patientId or referrerId is required", requestId, correlationId);
            }
            if (list == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No teleconsult list payload returned", requestId, correlationId);
            }
            if (referralIdAlias != null && !referralIdAlias.isBlank()) {
                list = filterReferralsById(list, referralIdAlias);
            }
            return ok(normalizeReferralPayload(list), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * Governed RTC media token — provisions rtc-gateway session on first use, then issues scoped participant token.
     */
    @PostMapping("/sessions/{id}/media/token")
    public ResponseEntity<Map<String, Object>> issueMediaToken(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            String normalizedPurpose = telemedicineGovernanceService.normalizePurposeOfUse(purposeOfUse);
            JsonNode referral = pctClient.getReferral(id);
            if (referral == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No teleconsult session payload returned", requestId, correlationId);
            }
            if (consentBlocksMedia(referral)) {
                return error(HttpStatus.FORBIDDEN, "CONSENT_REQUIRED",
                        "Teleconsult consent must be granted before governed RTC media", requestId, correlationId);
            }

            String patientId = extractPatient(referral);
            String encounterId = val(body, "encounterId", "encounter_id");
            if (encounterId == null || encounterId.isBlank()) {
                encounterId = referral.path("encounterId").asText(referral.path("encounter_id").asText(null));
            }
            String identity = defaultString(actorId, val(body, "identity", "participantId"));
            String displayName = defaultString(val(body, "displayName", "display_name"), identity);
            String requestedRole = val(body, "role", "participantRole");
            String role;
            if (requestedRole != null && !requestedRole.isBlank()) {
                role = requestedRole.trim().toUpperCase(Locale.ROOT);
                if (!ALLOWED_PARTICIPANT_ROLES.contains(role)) {
                    return error(HttpStatus.BAD_REQUEST, "INVALID_PARTICIPANT_ROLE",
                            "role must be one of " + ALLOWED_PARTICIPANT_ROLES, requestId, correlationId);
                }
            } else {
                // Backward compat: default PROVIDER unless the caller identity is the referral patient.
                role = patientId != null && patientId.equals(identity) ? "PATIENT" : "PROVIDER";
            }
            ValidationError mediaProfileValidation = validateMediaProfile(val(body, "mediaProfile", "media_profile"));
            if (mediaProfileValidation != null) {
                return mediaProfileValidation.toResponse(requestId, correlationId);
            }
            String mediaProfile = normalizedMediaProfile(val(body, "mediaProfile", "media_profile"));

            if ("PATIENT".equals(role)) {
                ResponseEntity<Map<String, Object>> patientGovernanceError = assertPatientTokenGovernance(
                        id, patientId, identity, normalizedPurpose,
                        tenantId, correlationId, facilityId, actorId, requestId);
                if (patientGovernanceError != null) {
                    return patientGovernanceError;
                }
            }

            JsonNode rtcSession = provisionRtcSessionIfNeeded(
                    id, tenantId, normalizedPurpose, facilityId, referral, patientId, encounterId,
                    identity, displayName, role, mediaProfile);

            Map<String, Object> tokenBody = Map.of(
                    "participant", participantMap(identity, displayName, role, mediaProfile));
            JsonNode tokenResponse = rtcClient.issueParticipantToken(id, tokenBody);

            String gateStatus = waitingRoomGateStatus(tokenResponse);
            if (gateStatus != null) {
                // Frozen RTC contract: token issuance may answer WAITING/DENIED instead of a
                // token — pass it through so the web client can poll until admitted.
                if ("WAITING".equals(gateStatus) && "PATIENT".equals(role)) {
                    notifyProviderPatientWaiting(id, identity, referral);
                }
                Map<String, Object> gated = new LinkedHashMap<>();
                gated.put("referralId", id);
                gated.put("status", gateStatus);
                gated.put("sessionId", tokenResponse.path("sessionId").asText(id));
                gated.put("identity", tokenResponse.path("identity").asText(identity));
                if (tokenResponse.hasNonNull("reason")) {
                    gated.put("reason", tokenResponse.get("reason").asText());
                }
                telemedicineGovernanceService.audit(
                        tenantId, correlationId, normalizedPurpose, facilityId,
                        "TELEMEDICINE_RTC_TOKEN_" + gateStatus, "POST:teleconsult/media/token", "SUCCESS",
                        actorId, role, patientId, "TeleconsultSession", id, Map.of("provider", "rtc-gateway"));
                return ok(gated, requestId, correlationId, HttpStatus.OK);
            }

            Map<String, Object> media = new LinkedHashMap<>();
            mergeRtcFields(media, rtcSession);
            mergeRtcFields(media, tokenResponse);
            media.put("referralId", id);
            media.put("status", "READY");
            media.put("channel", media.getOrDefault("channel", "LIVEKIT"));

            telemedicineGovernanceService.audit(
                    tenantId, correlationId, normalizedPurpose, facilityId,
                    "TELEMEDICINE_RTC_TOKEN_ISSUED", "POST:teleconsult/media/token", "SUCCESS",
                    actorId, role, patientId, "TeleconsultSession", id, Map.of("provider", "rtc-gateway"));
            return ok(media, requestId, correlationId, HttpStatus.OK);
        } catch (HttpClientErrorException e) {
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * Refresh an RTC media token (provider or admitted participant). Straight passthrough to
     * rtc-gateway; WAITING/DENIED statuses are passed through like {@code media/token}.
     */
    @PostMapping("/sessions/{id}/media/token/refresh")
    public ResponseEntity<Map<String, Object>> refreshMediaToken(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            String normalizedPurpose = telemedicineGovernanceService.normalizePurposeOfUse(purposeOfUse);
            String identity = defaultString(actorId, val(body, "identity", "participantId"));
            String displayName = defaultString(val(body, "displayName", "display_name"), identity);
            String requestedRole = val(body, "role", "participantRole");
            String role = null;
            if (requestedRole != null && !requestedRole.isBlank()) {
                role = requestedRole.trim().toUpperCase(Locale.ROOT);
                if (!ALLOWED_PARTICIPANT_ROLES.contains(role)) {
                    return error(HttpStatus.BAD_REQUEST, "INVALID_PARTICIPANT_ROLE",
                            "role must be one of " + ALLOWED_PARTICIPANT_ROLES, requestId, correlationId);
                }
            }
            ValidationError mediaProfileValidation = validateMediaProfile(val(body, "mediaProfile", "media_profile"));
            if (mediaProfileValidation != null) {
                return mediaProfileValidation.toResponse(requestId, correlationId);
            }
            String mediaProfile = normalizedMediaProfile(val(body, "mediaProfile", "media_profile"));

            Map<String, Object> participant = participantMap(identity, displayName,
                    role == null ? "PROVIDER" : role, mediaProfile);
            JsonNode tokenResponse = rtcClient.refreshParticipantToken(id, Map.of("participant", participant));

            String gateStatus = waitingRoomGateStatus(tokenResponse);
            Map<String, Object> media = new LinkedHashMap<>();
            if (gateStatus != null) {
                media.put("status", gateStatus);
                media.put("sessionId", tokenResponse.path("sessionId").asText(id));
                media.put("identity", tokenResponse.path("identity").asText(identity));
            } else {
                mergeRtcFields(media, tokenResponse);
                media.put("status", "READY");
            }
            media.put("referralId", id);

            telemedicineGovernanceService.audit(
                    tenantId, correlationId, normalizedPurpose, facilityId,
                    "TELEMEDICINE_RTC_TOKEN_REFRESHED", "POST:teleconsult/media/token/refresh", "SUCCESS",
                    actorId, role == null ? "PROVIDER" : role, null, "TeleconsultSession", id,
                    Map.of("provider", "rtc-gateway"));
            return ok(media, requestId, correlationId, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            return error(status == null ? HttpStatus.BAD_REQUEST : status,
                    "TELEMEDICINE_GOVERNANCE_INVALID",
                    e.getReason() == null ? "Telemedicine governance rejected request" : e.getReason(),
                    requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * Waiting-room view for the provider console — RTC participants in WAITING state.
     * Caller must pass the existing telemedicine governance gate (provider-grade PDP policy);
     * a per-referral "is assigned provider" assertion is a seam noted in the module docs.
     */
    @GetMapping("/sessions/{id}/waiting-room")
    public ResponseEntity<Map<String, Object>> waitingRoom(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            ArrayNode waiting = filterWaitingParticipants(rtcClient.listParticipants(id));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessionId", id);
            data.put("waiting", waiting);
            return ok(data, requestId, correlationId, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            return error(status == null ? HttpStatus.FORBIDDEN : status,
                    "TELEMEDICINE_GOVERNANCE_INVALID",
                    e.getReason() == null ? "Telemedicine governance rejected request" : e.getReason(),
                    requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /** Admit a waiting participant and tell the patient the session is ready. */
    @PostMapping({"/sessions/{id}/admit", "/sessions/{id}/waiting-room/admit"})
    public ResponseEntity<Map<String, Object>> admitWaitingParticipant(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            String identity = val(body, "identity", "participantId");
            if (identity == null || identity.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "MISSING_IDENTITY",
                        "identity is required", requestId, correlationId);
            }
            JsonNode admitted = rtcClient.admitParticipant(id, identity.trim(), Map.of());
            sendSessionNotification("rtc.telemedicine.session-ready", identity.trim(), id,
                    "Session ready", "Your teleconsultation is ready — you have been admitted to the session.");
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_WAITING_ROOM_ADMITTED", "POST:teleconsult/waiting-room/admit", "SUCCESS",
                    actorId, "PROVIDER", identity.trim(), "TeleconsultSession", id, Map.of());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessionId", id);
            data.put("identity", identity.trim());
            data.put("state", admitted != null && admitted.hasNonNull("state")
                    ? admitted.get("state").asText() : "ADMITTED");
            return ok(data, requestId, correlationId, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            return error(status == null ? HttpStatus.FORBIDDEN : status,
                    "TELEMEDICINE_GOVERNANCE_INVALID",
                    e.getReason() == null ? "Telemedicine governance rejected request" : e.getReason(),
                    requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /** Deny a waiting participant ({identity, reason?}). */
    @PostMapping({"/sessions/{id}/deny", "/sessions/{id}/waiting-room/deny"})
    public ResponseEntity<Map<String, Object>> denyWaitingParticipant(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            String identity = val(body, "identity", "participantId");
            if (identity == null || identity.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "MISSING_IDENTITY",
                        "identity is required", requestId, correlationId);
            }
            String reason = val(body, "reason");
            Map<String, Object> denyBody = new LinkedHashMap<>();
            if (reason != null && !reason.isBlank()) {
                denyBody.put("reason", reason);
            }
            JsonNode denied = rtcClient.denyParticipant(id, identity.trim(), denyBody);
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_WAITING_ROOM_DENIED", "POST:teleconsult/waiting-room/deny", "SUCCESS",
                    actorId, "PROVIDER", identity.trim(), "TeleconsultSession", id,
                    reason == null ? Map.of() : Map.of("reason", reason));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessionId", id);
            data.put("identity", identity.trim());
            data.put("state", denied != null && denied.hasNonNull("state")
                    ? denied.get("state").asText() : "DENIED");
            return ok(data, requestId, correlationId, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            return error(status == null ? HttpStatus.FORBIDDEN : status,
                    "TELEMEDICINE_GOVERNANCE_INVALID",
                    e.getReason() == null ? "Telemedicine governance rejected request" : e.getReason(),
                    requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * Provider console aggregate — one round-trip for the console shell: referral detail,
     * waiting-room, recordings and encounter linkage.
     *
     * <p>Seam: billingStatus is intentionally omitted — COSTA exposes no cheap bill-by-encounter
     * read on this path; the console fetches billing lazily via the finance endpoints.</p>
     */
    @GetMapping("/sessions/{id}/console")
    public ResponseEntity<Map<String, Object>> providerConsole(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            JsonNode referral = pctClient.getReferral(id);
            if (referral == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No teleconsult session payload returned", requestId, correlationId);
            }
            Map<String, Object> console = new LinkedHashMap<>();
            console.put("referral", normalizeReferralPayload(referral));
            try {
                console.put("waitingRoom", filterWaitingParticipants(rtcClient.listParticipants(id)));
            } catch (Exception ex) {
                log.debug("Console waiting-room lookup failed for {}: {}", id, ex.getMessage());
                console.put("waitingRoom", objectMapper.createArrayNode());
            }
            try {
                JsonNode recordings = rtcClient.listRecordings(id);
                console.put("recordings", recordings != null && recordings.isArray()
                        ? recordings : objectMapper.createArrayNode());
            } catch (Exception ex) {
                log.debug("Console recordings lookup failed for {}: {}", id, ex.getMessage());
                console.put("recordings", objectMapper.createArrayNode());
            }
            console.put("encounterId", referral.path("encounterId").asText(
                    referral.path("encounter_id").asText(null)));
            return ok(console, requestId, correlationId, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            return error(status == null ? HttpStatus.FORBIDDEN : status,
                    "TELEMEDICINE_GOVERNANCE_INVALID",
                    e.getReason() == null ? "Telemedicine governance rejected request" : e.getReason(),
                    requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<Map<String, Object>> getSession(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            var referral = pctClient.getReferral(id);
            if (referral == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No teleconsult session payload returned", requestId, correlationId);
            }
            return ok(normalizeReferralPayload(referral), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/accept")
    public ResponseEntity<Map<String, Object>> accept(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            var accepted = pctClient.acceptReferral(id, Map.of(
                    "accepted_by", actorId != null ? actorId : "unknown"));
            emitTelemedicineNotification("TELECONSULT_ACCEPTED", accepted, actorId, "Teleconsult request accepted.");
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_REFERRAL_ACCEPTED", "POST:teleconsult/accept", "SUCCESS",
                    actorId, "PROVIDER", extractPatient(accepted), "TeleconsultReferral",
                    id, Map.of());
            return ok(accepted, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * Compatibility alias for mobile join semantics. Canonical action remains /accept.
     */
    @PostMapping("/sessions/{id}/join")
    public ResponseEntity<Map<String, Object>> join(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        return accept(id, requestId, correlationId, tenantId, purposeOfUse, facilityId, actorId);
    }

    @PostMapping("/sessions/{id}/decline")
    public ResponseEntity<Map<String, Object>> decline(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            String reason = val(body, "reason", "declineReason", "message");
            Map<String, Object> decline = new LinkedHashMap<>();
            decline.put("response_type", "DECLINED");
            decline.put("status", "DECLINED");
            decline.put("message", reason != null ? reason : "Declined by receiving specialist");
            if (actorId != null && !actorId.isBlank()) {
                decline.put("responder_id", actorId);
            }
            JsonNode response = pctClient.respondReferral(id, decline);
            if (response == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No decline payload returned", requestId, correlationId);
            }
            emitTelemedicineNotification("TELECONSULT_DECLINED", response, actorId, "Teleconsult request declined.");
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_REFERRAL_DECLINED", "POST:teleconsult/decline", "SUCCESS",
                    actorId, "PROVIDER", extractPatient(response), "TeleconsultReferral",
                    id, Map.of("reason", reason));
            return ok(response, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            String message = val(body, "message", "text", "content");
            if (message == null || message.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "message is required", requestId, correlationId);
            }
            Map<String, Object> note = new LinkedHashMap<>();
            note.put("response_type", "MESSAGE");
            note.put("channel", "ASYNCHRONOUS_NOTE");
            note.put("message", message);
            if (actorId != null && !actorId.isBlank()) {
                note.put("responder_id", actorId);
            }
            JsonNode response = pctClient.respondReferral(id, note);
            if (response == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No message payload returned", requestId, correlationId);
            }
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_MESSAGE_SENT", "POST:teleconsult/messages", "SUCCESS",
                    actorId, "PROVIDER", extractPatient(response), "TeleconsultReferral",
                    id, Map.of("messageLength", message.length()));
            return ok(response, requestId, correlationId, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/sessions/{id}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            JsonNode referral = pctClient.getReferral(id);
            if (referral == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No session payload returned for messages", requestId, correlationId);
            }
            return ok(extractMessageThread(referral), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/response")
    public ResponseEntity<Map<String, Object>> submitResponse(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            var response = pctClient.respondReferral(id, body);
            if (response == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No consultation response payload returned", requestId, correlationId);
            }
            return ok(response, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/complete")
    public ResponseEntity<Map<String, Object>> complete(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            if (Boolean.parseBoolean(val(body, "breakGlassOverride", "break_glass_override"))) {
                String reason = val(body, "breakGlassReason", "break_glass_reason", "reason");
                String approver = val(body, "breakGlassApprovedBy", "break_glass_approved_by");
                if (reason == null || reason.isBlank() || approver == null || approver.isBlank()) {
                    return error(HttpStatus.BAD_REQUEST, "BREAK_GLASS_REQUIREMENTS_MISSING",
                            "breakGlassReason and breakGlassApprovedBy are required for override completion",
                            requestId, correlationId);
                }
                telemedicineGovernanceService.assertBreakGlassOverrideAllowed();
            }
            // Finalise billing context at completion (the point the value-trigger fires); falls
            // back to what was captured at referral creation when not resolvable here.
            Map<String, Object> completionBody = body == null
                    ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(body);
            applyBillingContext(completionBody, val(completionBody, "patientId", "patient_id"), facilityId, completionBody);
            var completed = pctClient.completeReferral(id, completionBody);
            if (completed == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No completion payload returned", requestId, correlationId);
            }
            emitTelemedicineNotification("TELECONSULT_COMPLETED", completed, actorId, "Teleconsult session completed.");
            emitTelemedicineAnalyticsEvent("TELECONSULT_COMPLETED", id, completed, actorId, facilityId, body);
            writeTeleconsultSummaryToFhir(id, completed, actorId);
            triggerTeleconsultBilling(id, completed, body);
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_REFERRAL_COMPLETED", "POST:teleconsult/complete", "SUCCESS",
                    actorId, "PROVIDER", extractPatient(completed), "TeleconsultReferral",
                    id, Map.of("breakGlass", Boolean.parseBoolean(val(body, "breakGlassOverride", "break_glass_override"))));
            return ok(completed, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    /**
     * Compatibility alias for legacy mobile "end" semantics. Canonical action remains /complete.
     */
    @PostMapping("/sessions/{id}/end")
    public ResponseEntity<Map<String, Object>> end(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        if (!payload.containsKey("outcome")) {
            payload.put("outcome", "COMPLETED");
        }
        return complete(id, requestId, correlationId, tenantId, purposeOfUse, facilityId, actorId, payload);
    }

    @GetMapping("/routing/providers")
    public ResponseEntity<Map<String, Object>> searchProviders(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("query", query);
            request.put("page", page);
            request.put("size", Math.min(Math.max(size, 1), 50));
            JsonNode providers = varapiClient.searchProviders(request);
            return ok(providers, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("VARAPI_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/routing/facilities")
    public ResponseEntity<Map<String, Object>> searchFacilities(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("query", query);
            request.put("page", page);
            request.put("size", Math.min(Math.max(size, 1), 50));
            JsonNode facilities = tusoClient.searchFacilities(request);
            return ok(facilities, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("TUSO_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/routing/workspaces")
    public ResponseEntity<Map<String, Object>> listWorkspaces(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            long parsedFacilityId = Long.parseLong(facilityId);
            JsonNode workspaces = tusoClient.listWorkspaces(parsedFacilityId);
            return ok(workspaces, requestId, correlationId, HttpStatus.OK);
        } catch (NumberFormatException nfe) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_FACILITY_ID",
                    "facility_id must be numeric", requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("TUSO_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private int inferStage(Map<String, Object> body) {
        if (body.containsKey("type") || body.containsKey("consentType")) return 7;
        if (body.containsKey("preferredMode")) return 6;
        if (body.containsKey("routingType")) return 5;
        if (body.containsKey("attachments")) return 4;
        if (body.containsKey("visitSummary")) return 3;
        if (body.containsKey("patientSummary")) return 2;
        return 1;
    }

    private String val(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) return value.toString();
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Enrich a referral payload with billing context — {@code patient_category} (from
     * coverage-service) and {@code facility_category} (from the TUSO facility registry) — so
     * COSTA's charging rules (exemptions/waivers/surcharges) can fire when the teleconsult is
     * priced. Caller-supplied values win. Best-effort: any lookup failure, or a non-numeric
     * facility reference, leaves the field unset and PCT/COSTA degrade gracefully.
     */
    private void applyBillingContext(Map<String, Object> payload, String patientCpid,
                                     String facilityRef, Map<String, Object> body) {
        String patientCategory = val(body, "patientCategory", "patient_category");
        if ((patientCategory == null || patientCategory.isBlank())
                && patientCpid != null && !patientCpid.isBlank()) {
            try {
                patientCategory = nodeText(coverageClient.resolvePatientCategory(patientCpid), "category");
            } catch (Exception e) {
                log.debug("Coverage patient-category lookup failed for {}: {}", patientCpid, e.getMessage());
            }
        }
        if (patientCategory != null && !patientCategory.isBlank()) {
            payload.put("patient_category", patientCategory);
        }

        String facilityCategory = val(body, "facilityCategory", "facility_category");
        if ((facilityCategory == null || facilityCategory.isBlank())
                && facilityRef != null && facilityRef.matches("\\d+")) {
            try {
                JsonNode facility = tusoClient.getFacility(Long.parseLong(facilityRef));
                facilityCategory = firstNonBlank(
                        nodeText(facility, "facilityCategory"),
                        nodeText(facility, "level"));
            } catch (Exception e) {
                log.debug("TUSO facility-category lookup failed for {}: {}", facilityRef, e.getMessage());
            }
        }
        if (facilityCategory != null && !facilityCategory.isBlank()) {
            payload.put("facility_category", facilityCategory);
        }
    }

    private String nodeText(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private ResponseEntity<Map<String, Object>> ok(Object data, String requestId, String correlationId, HttpStatus status) {
        return ResponseEntity.status(status).body(Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(String code, String message, String requestId, String correlationId) {
        log.warn("Teleconsult upstream failure: {}", message);
        return error(HttpStatus.BAD_GATEWAY, code,
                message != null ? message : "Teleconsult upstream unavailable", requestId, correlationId);
    }

    private ValidationError validateStoredRoutingAndAttachments(JsonNode referral) {
        if (referral == null || referral.isNull()) {
            return new ValidationError(HttpStatus.BAD_GATEWAY, "PCT_UNAVAILABLE", "Unable to load referral for pre-submit validation");
        }
        List<String> attachments = extractAttachmentReferencesFromReferral(referral);
        ValidationError attachmentValidation = validateAttachmentReferences(attachments);
        if (attachmentValidation != null) return attachmentValidation;

        String routingType = null;
        String routingTarget = null;
        JsonNode routingNode = parseRoutingTarget(referral.path("routingTarget"));
        if (routingNode != null && routingNode.isObject()) {
            routingType = routingNode.path("type").asText(null);
            routingTarget = routingNode.hasNonNull("target_ref")
                    ? routingNode.path("target_ref").asText()
                    : routingNode.path("target").asText(null);
        }
        return validateRoutingTarget(normalizedRoutingType(routingType), routingTarget, Map.of());
    }

    private ValidationError validateAttachmentReferences(List<String> attachmentRefs) {
        for (String ref : attachmentRefs) {
            UUID documentId;
            try {
                documentId = UUID.fromString(ref);
            } catch (IllegalArgumentException ex) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_REFERENCE",
                        "Attachment reference is not a UUID: " + ref);
            }
            try {
                documentClient.getObjectMetadata(documentId);
            } catch (HttpClientErrorException.NotFound notFound) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_REFERENCE",
                        "Attachment document id does not exist: " + documentId);
            } catch (HttpStatusCodeException clientError) {
                if (clientError.getStatusCode().value() == 403 || clientError.getStatusCode().value() == 401) {
                    return new ValidationError(HttpStatus.FORBIDDEN, "ATTACHMENT_INACCESSIBLE",
                            "Attachment is not accessible: " + documentId);
                }
                return new ValidationError(HttpStatus.BAD_GATEWAY, "DOCUMENT_SERVICE_UNAVAILABLE",
                        "Document service lookup failed for attachment validation");
            } catch (ResourceAccessException ex) {
                return new ValidationError(HttpStatus.BAD_GATEWAY, "DOCUMENT_SERVICE_UNAVAILABLE",
                        "Document service is unavailable for attachment validation");
            }
        }
        return null;
    }

    private ValidationError validateRoutingTarget(String routingType, String routingTarget, Map<String, Object> body) {
        if (routingType == null || routingType.isBlank()) {
            return null;
        }
        if (UNSUPPORTED_ROUTING_TYPES.contains(routingType)) {
            return new ValidationError(HttpStatus.NOT_IMPLEMENTED, "ROUTING_TYPE_UNAVAILABLE",
                    routingType + " routing requires future on-call/team/pool directory capability");
        }
        if (routingTarget == null || routingTarget.isBlank()) {
            return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                    "routingTarget is required when routingType is set");
        }
        if (PRACTITIONER_ROUTING_TYPES.contains(routingType)) {
            try {
                JsonNode provider = varapiClient.getProvider(routingTarget.trim());
                if (provider == null || provider.isNull()) {
                    return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                            "Provider routing target was not found in VARAPI");
                }
                return null;
            } catch (HttpClientErrorException.NotFound notFound) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "Provider routing target was not found in VARAPI");
            } catch (Exception e) {
                return new ValidationError(HttpStatus.BAD_GATEWAY, "VARAPI_UNAVAILABLE",
                        "VARAPI provider lookup failed for routing validation");
            }
        }
        if (TEAM_ROUTING_TYPES.contains(routingType)) {
            if (routingTarget.trim().length() < 3) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        routingType + " routing target must identify a specialty/team key");
            }
            return null;
        }
        if (ON_CALL_ROUTING_TYPE.equals(routingType)) {
            // ON_CALL target is a specialty key; the concrete provider is resolved from the
            // khuluma on-call roster at submit time.
            if (routingTarget.trim().length() < 3) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "ON_CALL routing target must identify a specialty key");
            }
            return null;
        }
        if ("WORKSPACE".equals(routingType)) {
            try {
                UUID workspaceId = UUID.fromString(routingTarget.trim());
                JsonNode workspace = tusoClient.getWorkspace(workspaceId);
                if (workspace == null || workspace.isNull()) {
                    return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                            "Workspace routing target was not found in TUSO");
                }
                return null;
            } catch (IllegalArgumentException ex) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "Workspace routing target must be a UUID");
            } catch (HttpClientErrorException.NotFound notFound) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "Workspace routing target was not found in TUSO");
            } catch (Exception e) {
                return new ValidationError(HttpStatus.BAD_GATEWAY, "TUSO_UNAVAILABLE",
                        "TUSO workspace lookup failed for routing validation");
            }
        }
        if ("UNIT".equals(routingType)) {
            return new ValidationError(HttpStatus.NOT_IMPLEMENTED, "ROUTING_TYPE_UNAVAILABLE",
                    "UNIT routing requires canonical facility-unit directory lookup support");
        }
        if ("FACILITY_SERVICE".equals(routingType)) {
            String facilityRef = routingTarget.trim();
            String[] parts = facilityRef.split(":", 2);
            try {
                long facilityId = Long.parseLong(parts[0]);
                JsonNode facility = tusoClient.getFacility(facilityId);
                if (facility == null || facility.isNull()) {
                    return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                            "Facility routing target was not found in TUSO");
                }
                if (parts.length == 2 && parts[1].isBlank()) {
                    return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                            "facility-service routing target must include a service code after ':'");
                }
                return null;
            } catch (NumberFormatException ex) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "facility-service routing target must start with numeric facility id");
            } catch (HttpClientErrorException.NotFound notFound) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "Facility routing target was not found in TUSO");
            } catch (Exception e) {
                return new ValidationError(HttpStatus.BAD_GATEWAY, "TUSO_UNAVAILABLE",
                        "TUSO facility lookup failed for routing validation");
            }
        }
        return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                "Unsupported routingType: " + routingType);
    }

    private List<String> extractAttachmentReferences(Object rawAttachments) {
        if (rawAttachments == null) {
            return List.of();
        }
        if (rawAttachments instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }
        if (rawAttachments instanceof String single) {
            String trimmed = single.trim();
            if (trimmed.isBlank()) return List.of();
            return List.of(trimmed);
        }
        return List.of(rawAttachments.toString().trim());
    }

    private List<String> extractAttachmentReferencesFromReferral(JsonNode referral) {
        JsonNode attachmentsNode = referral.path("attachmentDocumentIds");
        if (attachmentsNode.isMissingNode() || attachmentsNode.isNull()) {
            attachmentsNode = referral.path("attachments");
        }
        if (attachmentsNode.isMissingNode() || attachmentsNode.isNull()) {
            return List.of();
        }
        if (attachmentsNode.isArray()) {
            List<String> refs = new ArrayList<>();
            attachmentsNode.forEach(node -> {
                String ref = node.asText("").trim();
                if (!ref.isBlank()) refs.add(ref);
            });
            return refs.stream().distinct().toList();
        }
        if (attachmentsNode.isTextual()) {
            String raw = attachmentsNode.asText("");
            if (raw.isBlank()) return List.of();
            try {
                JsonNode parsed = objectMapper.readTree(raw);
                if (parsed.isArray()) {
                    List<String> refs = new ArrayList<>();
                    parsed.forEach(node -> {
                        String ref = node.asText("").trim();
                        if (!ref.isBlank()) refs.add(ref);
                    });
                    return refs.stream().distinct().toList();
                }
            } catch (JsonProcessingException ignored) {
                // fallback handled below
            }
            return List.of(raw.trim());
        }
        return List.of();
    }

    private String normalizedRoutingType(String routingType) {
        return routingType == null ? null : routingType.trim().toUpperCase(Locale.ROOT);
    }

    private Object normalizeReferralPayload(Object payload) {
        if (payload instanceof JsonNode jsonNode) {
            return normalizeReferralJson(jsonNode);
        }
        return payload;
    }

    private JsonNode normalizeReferralJson(JsonNode input) {
        if (input == null || input.isNull()) return input;
        if (input.isArray()) {
            ArrayNode normalized = objectMapper.createArrayNode();
            input.forEach(node -> normalized.add(normalizeReferralJson(node)));
            return normalized;
        }
        if (!input.isObject()) return input;
        ObjectNode copy = input.deepCopy();
        List<String> attachments = extractAttachmentReferencesFromReferral(copy);
        ArrayNode attachmentRefs = objectMapper.createArrayNode();
        attachments.forEach(attachmentRefs::add);
        copy.set("attachmentReferences", attachmentRefs);

        JsonNode routingNode = parseRoutingTarget(copy.path("routingTarget"));
        if (routingNode != null && routingNode.isObject()) {
            copy.put("routingType", routingNode.path("type").asText(""));
            String targetRef = routingNode.hasNonNull("target_ref")
                    ? routingNode.path("target_ref").asText("")
                    : routingNode.path("target").asText("");
            copy.put("routingTargetRef", targetRef);
        }
        return copy;
    }

    private JsonNode filterReferralsByStatus(JsonNode payload, String status) {
        if (status == null || status.isBlank() || payload == null || payload.isNull()) {
            return payload;
        }
        String expected = status.trim().toUpperCase(Locale.ROOT);
        if (payload.isArray()) {
            ArrayNode filtered = objectMapper.createArrayNode();
            payload.forEach(node -> {
                String nodeStatus = node.path("status").asText("");
                if (expected.equals(nodeStatus.toUpperCase(Locale.ROOT))) {
                    filtered.add(node);
                }
            });
            return filtered;
        }
        if (payload.isObject()) {
            ObjectNode copy = payload.deepCopy();
            JsonNode items = copy.path("items");
            if (items.isArray()) {
                ArrayNode filtered = objectMapper.createArrayNode();
                items.forEach(node -> {
                    String nodeStatus = node.path("status").asText("");
                    if (expected.equals(nodeStatus.toUpperCase(Locale.ROOT))) {
                        filtered.add(node);
                    }
                });
                copy.set("items", filtered);
                return copy;
            }
        }
        return payload;
    }

    private JsonNode filterReferralsById(JsonNode payload, String referralId) {
        if (referralId == null || referralId.isBlank() || payload == null || payload.isNull()) {
            return payload;
        }
        String expected = referralId.trim();
        if (payload.isArray()) {
            ArrayNode filtered = objectMapper.createArrayNode();
            payload.forEach(node -> {
                String id = node.path("id").asText("");
                if (expected.equals(id)) {
                    filtered.add(node);
                }
            });
            return filtered;
        }
        if (payload.isObject()) {
            ObjectNode copy = payload.deepCopy();
            JsonNode items = copy.path("items");
            if (items.isArray()) {
                ArrayNode filtered = objectMapper.createArrayNode();
                items.forEach(node -> {
                    String id = node.path("id").asText("");
                    if (expected.equals(id)) {
                        filtered.add(node);
                    }
                });
                copy.set("items", filtered);
                return copy;
            }
        }
        return payload;
    }

    private JsonNode extractMessageThread(JsonNode referral) {
        if (referral == null || referral.isNull()) {
            return objectMapper.createArrayNode();
        }
        JsonNode fromMessages = referral.path("messages");
        if (fromMessages.isArray()) {
            return fromMessages;
        }
        JsonNode fromResponses = referral.path("responses");
        if (fromResponses.isArray()) {
            return fromResponses;
        }
        return objectMapper.createArrayNode();
    }

    private JsonNode parseRoutingTarget(JsonNode routingNode) {
        if (routingNode == null || routingNode.isNull() || routingNode.isMissingNode()) return null;
        if (routingNode.isObject()) return routingNode;
        if (routingNode.isTextual()) {
            String raw = routingNode.asText("");
            if (raw.isBlank()) return null;
            try {
                JsonNode parsed = objectMapper.readTree(raw);
                return parsed.isObject() ? parsed : null;
            } catch (JsonProcessingException ignored) {
                return null;
            }
        }
        return null;
    }

    @GetMapping("/ops/rtc-health")
    public ResponseEntity<Map<String, Object>> rtcOpsHealth(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            JsonNode health = rtcClient.getOpsHealth();
            if (health == null) {
                return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", "No RTC ops health payload returned", requestId, correlationId);
            }
            return ok(health, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/ops/sla")
    public ResponseEntity<Map<String, Object>> telemedicineOpsSla(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityHeader,
            @RequestParam(name = "facility_id", required = false) String facilityId) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            String resolvedFacility = facilityId != null && !facilityId.isBlank() ? facilityId : facilityHeader;
            if (resolvedFacility == null || resolvedFacility.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "MISSING_FACILITY_ID",
                        "facility_id query param or X-Facility-ID header is required", requestId, correlationId);
            }
            JsonNode payload = pctClient.getTelemedicineOps(resolvedFacility);
            if (payload == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No telemedicine ops payload returned", requestId, correlationId);
            }
            return ok(payload, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/ops/specialty-workbench")
    public ResponseEntity<Map<String, Object>> specialtyWorkbench(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityHeader,
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestParam(required = false) String specialty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            String resolvedFacility = facilityId != null && !facilityId.isBlank() ? facilityId : facilityHeader;
            if (resolvedFacility == null || resolvedFacility.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "MISSING_FACILITY_ID",
                        "facility_id query param or X-Facility-ID header is required", requestId, correlationId);
            }
            JsonNode incoming = pctClient.listIncomingReferrals(resolvedFacility, "SUBMITTED", page, Math.min(Math.max(size, 1), 100));
            ArrayNode rows = objectMapper.createArrayNode();
            if (incoming != null && incoming.isArray()) {
                for (JsonNode row : incoming) {
                    String rowSpecialty = row.path("specialty").asText("");
                    if (specialty == null || specialty.isBlank() || specialty.equalsIgnoreCase(rowSpecialty)) {
                        rows.add(row);
                    }
                }
            }
            return ok(rows, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private void emitTelemedicineAnalyticsEvent(String eventType,
                                                String sessionId,
                                                JsonNode source,
                                                String actorId,
                                                String facilityId,
                                                Map<String, Object> body) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", eventType);
            event.put("sessionId", sessionId);
            event.put("referralId", sessionId);
            if (facilityId != null && !facilityId.isBlank()) {
                event.put("facilityId", facilityId);
            }
            event.put("actorId", defaultString(actorId, "unknown"));
            String patientId = extractPatient(source);
            if (patientId != null && !patientId.isBlank()) {
                event.put("patientId", patientId);
            }
            event.put("occurredAt", OffsetDateTime.now().toString());
            if (body != null) {
                copyAnalyticsField(body, event, "outcome");
                copyAnalyticsField(body, event, "durationMinutes", "duration_minutes");
                copyAnalyticsField(body, event, "specialty");
            }
            analyticsClient.ingestTelemedicineEvent(event);
        } catch (Exception ex) {
            log.warn("Telemedicine analytics emission failed: {}", ex.getMessage());
        }
    }

    private static void copyAnalyticsField(Map<String, Object> from, Map<String, Object> to, String... keys) {
        for (String key : keys) {
            Object value = from.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                to.put(keys[0], value);
                return;
            }
        }
    }

    private void emitTelemedicineNotification(String templateKey, JsonNode source, String actorId, String message) {
        try {
            String recipient = extractPatient(source);
            if (recipient == null || recipient.isBlank()) {
                return;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("templateKey", templateKey);
            body.put("channel", "IN_APP");
            body.put("to", recipient);
            body.put("variables", Map.of(
                    "actorId", defaultString(actorId, "unknown"),
                    "message", message
            ));
            notificationClient.sendNotification(body);
        } catch (Exception ex) {
            log.warn("Telemedicine notification emission failed: {}", ex.getMessage());
        }
    }

    private void writeTeleconsultSummaryToFhir(String referralId, JsonNode completed, String actorId) {
        try {
            String patientRef = extractPatient(completed);
            if (patientRef == null || patientRef.isBlank()) {
                return;
            }
            ObjectNode diagnosticReport = objectMapper.createObjectNode();
            diagnosticReport.put("resourceType", "DiagnosticReport");
            diagnosticReport.put("status", "final");
            diagnosticReport.put("code", "teleconsult-summary");
            diagnosticReport.put("subject", "Patient/" + patientRef);
            diagnosticReport.put("issued", OffsetDateTime.now().toString());
            diagnosticReport.put("conclusion", "Teleconsult referral " + referralId + " completed.");
            diagnosticReport.put("performer", defaultString(actorId, "unknown"));
            fhirGatewayClient.createResource("DiagnosticReport", diagnosticReport);
        } catch (Exception ex) {
            log.warn("Teleconsult FHIR writeback failed: {}", ex.getMessage());
        }
    }

    private void triggerTeleconsultBilling(String referralId, JsonNode completed, Map<String, Object> body) {
        try {
            String encounterId = val(body, "encounterId", "encounter_id");
            if (encounterId == null || encounterId.isBlank()) {
                encounterId = completed != null && completed.path("encounterId").isTextual()
                        ? completed.path("encounterId").asText()
                        : referralId;
            }
            JsonNode billDraft = costaClient.createBillDraft(encounterId, "ENCOUNTER");
            if (billDraft == null || !billDraft.has("id")) {
                return;
            }
            String billId = billDraft.path("id").asText();
            costaClient.submitForApproval(billId);
            costaClient.approveBill(billId, "Auto-approved teleconsult completion");
            costaClient.finalizeBill(billId);
        } catch (Exception ex) {
            log.warn("Teleconsult billing trigger failed: {}", ex.getMessage());
        }
    }

    private String extractId(JsonNode node) {
        if (node == null) return "";
        if (node.hasNonNull("id")) return node.get("id").asText();
        return "";
    }

    private String extractPatient(JsonNode node) {
        if (node == null) return null;
        if (node.hasNonNull("patientCpid")) return node.get("patientCpid").asText();
        if (node.hasNonNull("patient_id")) return node.get("patient_id").asText();
        if (node.hasNonNull("patientId")) return node.get("patientId").asText();
        return null;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean consentBlocksMedia(JsonNode referral) {
        String status = referral.path("consentStatus").asText(referral.path("consent_status").asText(""));
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("DENIED") || normalized.contains("REVOKED") || normalized.contains("REFUSED");
    }

    private JsonNode provisionRtcSessionIfNeeded(String sessionId,
                                                 String tenantId,
                                                 String purposeOfUse,
                                                 String facilityId,
                                                 JsonNode referral,
                                                 String patientId,
                                                 String encounterId,
                                                 String identity,
                                                 String displayName,
                                                 String role,
                                                 String mediaProfile) {
        try {
            JsonNode existing = rtcClient.getSession(sessionId);
            if (existing != null && !existing.isNull()) {
                return existing;
            }
        } catch (HttpClientErrorException.NotFound notFound) {
            // provision below
        } catch (Exception ex) {
            log.debug("RTC session lookup skipped for {}: {}", sessionId, ex.getMessage());
        }

        Map<String, Object> provision = new LinkedHashMap<>();
        provision.put("tenantId", defaultString(tenantId, "default"));
        provision.put("sessionId", sessionId);
        provision.put("referralId", sessionId);
        provision.put("encounterId", encounterId);
        provision.put("patientId", defaultString(patientId, "unknown"));
        provision.put("providerId", identity);
        provision.put("facilityId", facilityId);
        provision.put("purposeOfUse", purposeOfUse);
        provision.put("consentReference", referral.path("consentReference").asText(
                referral.path("consent_reference").asText(null)));
        provision.put("sessionType", "TELECONSULT");
        provision.put("participant", participantMap(identity, displayName, role, mediaProfile));
        return rtcClient.provisionSession(provision);
    }

    /**
     * ON_CALL routing resolution (submit path): the stored routing target is a specialty key;
     * look up the khuluma specialty-scoped on-call roster, pick the first on-call provider,
     * rewrite the referral routing to that PRACTITIONER (mirroring practitioner routing) and
     * notify them. No on-call provider is an honest 422 NO_ON_CALL_PROVIDER.
     *
     * @return an error response when submission must halt, otherwise {@code null}.
     */
    private ResponseEntity<Map<String, Object>> resolveOnCallRoutingIfNeeded(
            String id, JsonNode referral, String tenantId, String correlationId, String purposeOfUse,
            String facilityId, String actorId, String requestId) {
        JsonNode routingNode = referral == null ? null : parseRoutingTarget(referral.path("routingTarget"));
        if (routingNode == null || !routingNode.isObject()) {
            return null;
        }
        String routingType = normalizedRoutingType(routingNode.path("type").asText(null));
        if (!ON_CALL_ROUTING_TYPE.equals(routingType)) {
            return null;
        }
        String specialty = routingNode.hasNonNull("target_ref")
                ? routingNode.path("target_ref").asText("")
                : routingNode.path("target").asText("");
        JsonNode roster;
        try {
            roster = khulumaClient.onCallRoster(tenantId, specialty);
        } catch (Exception ex) {
            log.warn("Khuluma on-call roster lookup failed for referral {}: {}", id, ex.getMessage());
            return error(HttpStatus.BAD_GATEWAY, "KHULUMA_UNAVAILABLE",
                    "On-call roster lookup failed", requestId, correlationId);
        }
        String provider = null;
        if (roster != null && roster.isArray()) {
            for (JsonNode entry : roster) {
                String actor = entry.path("actorId").asText("");
                if (!actor.isBlank()) {
                    provider = actor;
                    break;
                }
            }
        }
        if (provider == null) {
            return error(HttpStatus.UNPROCESSABLE_ENTITY, "NO_ON_CALL_PROVIDER",
                    "No on-call provider is available"
                            + (specialty == null || specialty.isBlank() ? "" : " for specialty " + specialty),
                    requestId, correlationId);
        }
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("stage", 5);
        update.put("routing_target", Map.of("type", "PRACTITIONER", "target_ref", provider));
        pctClient.updateReferralStage(id, update);
        sendSessionNotification("TELECONSULT_REQUESTED", provider, id,
                "On-call teleconsult assigned",
                "You have been assigned an on-call teleconsult referral " + id + ".");
        telemedicineGovernanceService.audit(
                tenantId, correlationId, purposeOfUse, facilityId,
                "TELEMEDICINE_ON_CALL_ROUTED", "POST:teleconsult/submit", "SUCCESS",
                actorId, "PROVIDER", extractPatient(referral), "TeleconsultReferral", id,
                Map.of("specialty", defaultString(specialty, ""), "provider", provider));
        return null;
    }

    private Map<String, Object> participantMap(String identity, String displayName, String role, String mediaProfile) {
        Map<String, Object> participant = new LinkedHashMap<>();
        participant.put("identity", identity);
        participant.put("displayName", displayName);
        participant.put("role", role);
        if (mediaProfile != null) {
            participant.put("mediaProfile", mediaProfile);
        }
        return participant;
    }

    private ValidationError validateMediaProfile(String rawMediaProfile) {
        if (rawMediaProfile == null || rawMediaProfile.isBlank()) {
            return null;
        }
        String normalized = rawMediaProfile.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_MEDIA_PROFILES.contains(normalized)) {
            return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_MEDIA_PROFILE",
                    "mediaProfile must be one of " + ALLOWED_MEDIA_PROFILES);
        }
        return null;
    }

    private String normalizedMediaProfile(String rawMediaProfile) {
        if (rawMediaProfile == null || rawMediaProfile.isBlank()) {
            return null;
        }
        return rawMediaProfile.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Governance for PATIENT-role media tokens.
     *
     * <p>When the referral carries a patient anchor (CPID), the caller identity must equal it —
     * a PATIENT token is only issuable to the referral's own patient. When no patient anchor is
     * derivable, the current governance model cannot assert caller↔patient linkage at the BFF
     * (identity-plane subject-relationship lookup is a noted seam), so we fail closed to
     * purpose-of-use TREATMENT and write an explicit audit event for the unverified linkage.</p>
     *
     * @return an error response when the request must be rejected, otherwise {@code null}.
     */
    private ResponseEntity<Map<String, Object>> assertPatientTokenGovernance(
            String sessionId, String referralPatient, String identity, String normalizedPurpose,
            String tenantId, String correlationId, String facilityId, String actorId, String requestId) {
        if (referralPatient != null && !referralPatient.isBlank()) {
            // The referral anchor is a CPID while the caller identity is a health
            // anchor — compare in both spaces. Direct anchor match covers referrals
            // that carry an anchor; otherwise resolve the caller's patient record
            // via VITO and compare CPIDs.
            if (referralPatient.equals(identity)) {
                return null;
            }
            String callerPatient = resolveCallerPatientAnchor(actorId);
            if (callerPatient != null && referralPatient.equals(callerPatient)) {
                return null;
            }
            if (callerPatient != null) {
                // Verified linkage AND verified mismatch — hard deny.
                telemedicineGovernanceService.audit(
                        tenantId, correlationId, normalizedPurpose, facilityId,
                        "TELEMEDICINE_PATIENT_TOKEN_DENIED", "POST:teleconsult/media/token", "DENIED",
                        actorId, "PATIENT", referralPatient, "TeleconsultSession", sessionId,
                        Map.of("reason", "caller's patient record does not match the referral patient",
                                "callerPatient", callerPatient));
                return error(HttpStatus.FORBIDDEN, "PATIENT_IDENTITY_MISMATCH",
                        "A PATIENT-role media token may only be requested by the referral's patient",
                        requestId, correlationId);
            }
            // Linkage unverifiable (no caller↔patient link on this identity plane):
            // fail toward the WAITING ROOM, not open media — the token request still
            // lands the caller in the lobby and the provider's admit is the human
            // verification. Requires TREATMENT purpose; fully audited.
            if (!"TREATMENT".equals(normalizedPurpose)) {
                return error(HttpStatus.FORBIDDEN, "PATIENT_LINKAGE_UNVERIFIED",
                        "PATIENT-role media tokens require purpose-of-use TREATMENT when the caller's "
                                + "patient linkage cannot be verified", requestId, correlationId);
            }
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, normalizedPurpose, facilityId,
                    "TELEMEDICINE_PATIENT_TOKEN_LINKAGE_UNVERIFIED", "POST:teleconsult/media/token", "SUCCESS",
                    actorId, "PATIENT", referralPatient, "TeleconsultSession", sessionId,
                    Map.of("reason", "caller patient linkage unverifiable; admitted to waiting room only"));
            return null;
        }
        if (!"TREATMENT".equals(normalizedPurpose)) {
            return error(HttpStatus.FORBIDDEN, "PATIENT_LINKAGE_UNVERIFIED",
                    "PATIENT-role media tokens require purpose-of-use TREATMENT when the referral "
                            + "carries no patient anchor", requestId, correlationId);
        }
        telemedicineGovernanceService.audit(
                tenantId, correlationId, normalizedPurpose, facilityId,
                "TELEMEDICINE_PATIENT_TOKEN_LINKAGE_UNVERIFIED", "POST:teleconsult/media/token", "SUCCESS",
                actorId, "PATIENT", null, "TeleconsultSession", sessionId,
                Map.of("reason", "referral has no derivable patient anchor; allowed under TREATMENT"));
        return null;
    }

    /**
     * Resolve the caller's patient anchor (CPID) from their health anchor via VITO.
     * Null when no linkage is derivable — the caller then only reaches the waiting room.
     */
    private String resolveCallerPatientAnchor(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            return null;
        }
        try {
            JsonNode identity = vitoClient.resolveIdentity(actorId);
            if (identity == null || identity.isNull()) {
                return null;
            }
            JsonNode node = identity.has("data") ? identity.get("data") : identity;
            JsonNode attrs = node.has("attributes") ? node.get("attributes") : node;
            for (String field : new String[]{"cpid", "patient_id", "patientId"}) {
                if (attrs.hasNonNull(field)) {
                    return attrs.get(field).asText();
                }
                if (node.hasNonNull(field)) {
                    return node.get(field).asText();
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Caller patient-anchor resolution failed for {}: {}", actorId, e.getMessage());
            return null;
        }
    }

    /**
     * WAITING/DENIED gate detection per the frozen RTC contract: a token response without a
     * token/room credential and a WAITING or DENIED status means the participant is being held
     * at the waiting-room gate.
     */
    private String waitingRoomGateStatus(JsonNode tokenResponse) {
        if (tokenResponse == null || tokenResponse.isNull()) {
            return null;
        }
        if (tokenResponse.hasNonNull("accessToken") || tokenResponse.hasNonNull("token")
                || tokenResponse.hasNonNull("access_token") || tokenResponse.hasNonNull("roomUrl")
                || tokenResponse.hasNonNull("room_url")) {
            return null;
        }
        String status = tokenResponse.path("status").asText("").trim().toUpperCase(Locale.ROOT);
        return "WAITING".equals(status) || "DENIED".equals(status) ? status : null;
    }

    /**
     * Notify the referral's provider that a patient is waiting. The BFF cannot observe the RTC
     * participant-waiting Kafka event, so this fires when a PATIENT token request returns
     * WAITING; a short in-memory TTL cache dedupes the poll loop (per BFF instance — repeats
     * across instances/restarts are accepted and noted).
     */
    private void notifyProviderPatientWaiting(String sessionId, String identity, JsonNode referral) {
        try {
            String recipient = extractProviderRef(referral);
            if (recipient == null || recipient.isBlank()) {
                return;
            }
            long now = System.currentTimeMillis();
            String dedupeKey = sessionId + ":" + defaultString(identity, "unknown");
            Long lastSent = waitingNotificationSentAt.get(dedupeKey);
            if (lastSent != null && now - lastSent < WAITING_NOTIFICATION_TTL_MS) {
                return;
            }
            waitingNotificationSentAt.put(dedupeKey, now);
            waitingNotificationSentAt.entrySet().removeIf(e -> now - e.getValue() > WAITING_NOTIFICATION_TTL_MS);
            sendSessionNotification("rtc.telemedicine.patient-waiting", recipient, sessionId,
                    "Patient waiting",
                    "A patient is waiting in the teleconsult waiting room for session " + sessionId + ".");
        } catch (Exception ex) {
            log.warn("Telemedicine patient-waiting notification failed: {}", ex.getMessage());
        }
    }

    /** Best-effort IN_APP notification on a session template key; never fails the request. */
    private void sendSessionNotification(String templateKey, String recipient, String sessionId,
                                         String title, String message) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("templateKey", templateKey);
            body.put("channel", "IN_APP");
            body.put("to", recipient);
            body.put("variables", Map.of(
                    "sessionId", sessionId,
                    "title", title,
                    "message", message));
            notificationClient.sendNotification(body);
        } catch (Exception ex) {
            log.warn("Telemedicine notification '{}' emission failed: {}", templateKey, ex.getMessage());
        }
    }

    private String extractProviderRef(JsonNode referral) {
        if (referral == null) {
            return null;
        }
        for (String key : new String[]{"assignedProviderId", "assigned_provider_id",
                "providerId", "provider_id", "referrerId", "referrer_id"}) {
            if (referral.hasNonNull(key)) {
                String value = referral.get(key).asText("");
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private ArrayNode filterWaitingParticipants(JsonNode participants) {
        ArrayNode waiting = objectMapper.createArrayNode();
        if (participants != null && participants.isArray()) {
            for (JsonNode participant : participants) {
                if ("WAITING".equalsIgnoreCase(participant.path("state").asText(""))) {
                    waiting.add(participant);
                }
            }
        }
        return waiting;
    }

    /**
     * Scheduled teleconsult: create the booking-service appointment (type TELECONSULT, tagged
     * with the referral id in {@code notes} — the appointment aggregate has no dedicated
     * external-ref field), write the appointment linkage back onto the referral, and send the
     * booking confirmation notification.
     *
     * <p>Seams (honest): PCT's referral stage update ignores unknown keys, so the
     * {@code appointment_id} written on the referral PUT is forward-wiring only until PCT grows
     * the column — the durable link lives on the appointment's notes tag. notification-service's
     * NotifyRequest has no scheduledAt, so a time-of-appointment reminder cannot be scheduled —
     * we send an immediate confirmation on the reminder template instead.</p>
     */
    private JsonNode scheduleTeleconsultAppointment(JsonNode created, String scheduledAt,
                                                    String facilityHeader, String actorId,
                                                    Map<String, Object> body) {
        String referralId = extractId(created);
        String facilityRef = firstNonBlank(facilityHeader, val(body, "facilityId", "facility_id"));
        ObjectNode enriched = created != null && created.isObject()
                ? ((ObjectNode) created).deepCopy()
                : objectMapper.createObjectNode();
        enriched.put("scheduledAt", scheduledAt);
        if (facilityRef == null || facilityRef.isBlank()) {
            log.warn("Scheduled teleconsult {} skipped booking: no facility reference", referralId);
            enriched.put("appointmentError", "FACILITY_REQUIRED");
            return enriched;
        }
        try {
            Map<String, Object> appointment = new LinkedHashMap<>();
            appointment.put("patient_cpid", extractPatient(created) != null
                    ? extractPatient(created) : val(body, "patientId", "patient_id"));
            appointment.put("facility_id", facilityRef);
            appointment.put("provider_id", actorId);
            appointment.put("appointment_type", "TELECONSULT");
            appointment.put("scheduled_at", scheduledAt);
            appointment.put("reason", val(body, "clinicalQuestion", "reason"));
            appointment.put("notes", "teleconsult:referral:" + referralId);
            JsonNode appt = bookingClient.createAppointment(appointment);
            String appointmentId = appt != null && appt.hasNonNull("id") ? appt.get("id").asText() : null;
            if (appointmentId == null || appointmentId.isBlank()) {
                enriched.put("appointmentError", "BOOKING_UNAVAILABLE");
                return enriched;
            }
            enriched.put("appointmentId", appointmentId);
            try {
                Map<String, Object> referralUpdate = new LinkedHashMap<>();
                referralUpdate.put("stage", 1);
                referralUpdate.put("appointment_id", appointmentId);
                referralUpdate.put("scheduled_at", scheduledAt);
                pctClient.updateReferralStage(referralId, referralUpdate);
            } catch (Exception ex) {
                log.warn("Referral {} appointment linkage writeback failed: {}", referralId, ex.getMessage());
            }
            String patientRef = extractPatient(created);
            if (patientRef == null || patientRef.isBlank()) {
                patientRef = val(body, "patientId", "patient_id");
            }
            if (patientRef != null && !patientRef.isBlank()) {
                sendSessionNotification("rtc.telemedicine.appointment-reminder", patientRef, referralId,
                        "Teleconsultation scheduled",
                        "Your teleconsultation is scheduled for " + scheduledAt + ".");
            }
        } catch (Exception ex) {
            log.warn("Scheduled teleconsult {} booking failed: {}", referralId, ex.getMessage());
            enriched.put("appointmentError", "BOOKING_UNAVAILABLE");
        }
        return enriched;
    }

    private void mergeRtcFields(Map<String, Object> target, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        putIfPresent(target, node, "roomUrl", "room_url");
        putIfPresent(target, node, "accessToken", "token", "access_token");
        putIfPresent(target, node, "channel");
        putIfPresent(target, node, "status");
        putIfPresent(target, node, "roomName", "room_name");
    }

    private void putIfPresent(Map<String, Object> target, JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.hasNonNull(key)) {
                String value = node.get(key).asText();
                target.put(key, value);
                if ("roomUrl".equals(key)) {
                    target.put("room_url", value);
                }
                if ("accessToken".equals(key)) {
                    target.put("token", value);
                    target.put("access_token", value);
                }
                return;
            }
        }
    }

    private record ValidationError(HttpStatus status, String code, String message) {
        ResponseEntity<Map<String, Object>> toResponse(String requestId, String correlationId) {
            return ResponseEntity.status(status).body(Map.of(
                    "error", Map.of("code", code, "message", message),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
