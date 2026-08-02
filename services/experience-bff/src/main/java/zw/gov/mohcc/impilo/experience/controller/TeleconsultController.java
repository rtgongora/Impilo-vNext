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
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.FhirGatewayServiceClient;
import zw.gov.mohcc.impilo.experience.client.KhulumaServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.DaidzaiServiceClient;
import zw.gov.mohcc.impilo.experience.client.NhumeServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.client.RtcGatewayServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.telemedicine.TelemedicineBillingContextService;
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
    // TM-B3: POOL/NATIONAL_POOL are now served by the Vashandi virtual-pool directory (was 501).
    // UNIT remains genuinely unimplemented (needs a canonical facility-unit directory).
    private static final Set<String> UNSUPPORTED_ROUTING_TYPES = Set.of("UNIT");
    private static final Set<String> POOL_ROUTING_TYPES = Set.of("POOL", "NATIONAL_POOL");
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
    private final TelemedicineBillingContextService billingContextService;
    private final NotificationServiceClient notificationClient;
    private final FhirGatewayServiceClient fhirGatewayClient;
    private final CostaServiceClient costaClient;
    private final AnalyticsPipelineServiceClient analyticsClient;
    private final RtcGatewayServiceClient rtcClient;
    private final TelemedicineGovernanceService telemedicineGovernanceService;
    private final VitoServiceClient vitoClient;
    private final BookingServiceClient bookingClient;
    private final KhulumaServiceClient khulumaClient;
    private final OrosServiceClient orosClient;
    private final DaidzaiServiceClient daidzaiClient;
    private final NhumeServiceClient nhumeClient;
    private final VashandiServiceClient vashandiClient;
    private final zw.gov.mohcc.impilo.experience.telemedicine.TeleconsultResponseValidationService responseValidationService;
    private final ObjectMapper objectMapper;

    public TeleconsultController(PctServiceClient pctClient,
                                 VitoServiceClient vitoClient,
                                 MvumoServiceClient mvumoClient,
                                 DocumentServiceClient documentClient,
                                 VarapiServiceClient varapiClient,
                                 TusoServiceClient tusoClient,
                                 TelemedicineBillingContextService billingContextService,
                                 NotificationServiceClient notificationClient,
                                 FhirGatewayServiceClient fhirGatewayClient,
                                 CostaServiceClient costaClient,
                                 AnalyticsPipelineServiceClient analyticsClient,
                                 RtcGatewayServiceClient rtcClient,
                                 BookingServiceClient bookingClient,
                                 KhulumaServiceClient khulumaClient,
                                 OrosServiceClient orosClient,
                                 DaidzaiServiceClient daidzaiClient,
                                 NhumeServiceClient nhumeClient,
                                 VashandiServiceClient vashandiClient,
                                 zw.gov.mohcc.impilo.experience.telemedicine.TeleconsultResponseValidationService responseValidationService,
                                 TelemedicineGovernanceService telemedicineGovernanceService,
                                 ObjectMapper objectMapper) {
        this.pctClient = pctClient;
        this.vitoClient = vitoClient;
        this.mvumoClient = mvumoClient;
        this.documentClient = documentClient;
        this.varapiClient = varapiClient;
        this.tusoClient = tusoClient;
        this.billingContextService = billingContextService;
        this.notificationClient = notificationClient;
        this.fhirGatewayClient = fhirGatewayClient;
        this.costaClient = costaClient;
        this.analyticsClient = analyticsClient;
        this.rtcClient = rtcClient;
        this.bookingClient = bookingClient;
        this.khulumaClient = khulumaClient;
        this.orosClient = orosClient;
        this.daidzaiClient = daidzaiClient;
        this.nhumeClient = nhumeClient;
        this.vashandiClient = vashandiClient;
        this.responseValidationService = responseValidationService;
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
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("MVUMO_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
            return ok(sessionRows(normalizeReferralJson(list)), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e, requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e, requestId, correlationId);
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
            // TM-B2: for a POOL-routed case, surface queue depth + oldest-wait + a rough estimated
            // wait so the waiting-room UI can show "N ahead of you, ~M min" instead of an opaque spinner.
            data.put("poolContext", resolveWaitingRoomPoolContext(id));
            return ok(data, requestId, correlationId, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
            return error(status == null ? HttpStatus.FORBIDDEN : status,
                    "TELEMEDICINE_GOVERNANCE_INVALID",
                    e.getReason() == null ? "Telemedicine governance rejected request" : e.getReason(),
                    requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /** A rough per-consult minutes figure used only to turn queue depth into a human wait estimate. */
    private static final long ESTIMATED_MINUTES_PER_CONSULT = 15;

    /**
     * TM-B2: pool-queue context for a waiting POOL-routed referral — depth, oldest wait, and a rough
     * estimated wait (depth × per-consult minutes). Best-effort: any lookup failure returns null so
     * the waiting room still renders. Non-pool referrals return null (no queue context applies).
     */
    private Map<String, Object> resolveWaitingRoomPoolContext(String referralId) {
        try {
            JsonNode referral = pctClient.getReferral(referralId);
            if (referral == null) return null;
            String routingKind = referral.path("routingKind").asText(null);
            String poolId = referral.path("routingPoolId").asText(null);
            if (poolId == null || poolId.isBlank()) return null;
            if (routingKind != null && !"POOL".equalsIgnoreCase(routingKind) && !"NATIONAL_POOL".equalsIgnoreCase(routingKind)) {
                return null;
            }
            JsonNode stats = pctClient.getVirtualPoolStats(poolId);
            if (stats == null || !stats.isArray() || stats.isEmpty()) return null;
            long depth = 0;
            Long oldest = null;
            for (JsonNode q : stats) {
                depth += q.path("depth").asLong(0);
                if (q.hasNonNull("oldestWaitingMinutes")) {
                    long ow = q.path("oldestWaitingMinutes").asLong();
                    oldest = (oldest == null) ? ow : Math.max(oldest, ow);
                }
            }
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("poolId", poolId);
            ctx.put("depth", depth);
            ctx.put("oldestWaitingMinutes", oldest);
            ctx.put("estimatedWaitMinutes", depth * ESTIMATED_MINUTES_PER_CONSULT);
            return ctx;
        } catch (Exception ex) {
            log.debug("Pool context enrichment skipped for waiting-room {}: {}", referralId, ex.getMessage());
            return null;
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
            // The consultation has now actually begun. PCT is where a session's lifecycle is
            // recorded; without this the referral knew only when its paperwork was submitted, so
            // the telemedicine list could not tell a consult under way from one merely referred.
            // Admission is the join, so it is the start. A failure here must not deny an admitted
            // participant entry to the consultation — the clinical act has already happened.
            try {
                pctClient.markTeleconsultSessionStarted(id);
            } catch (Exception e) {
                log.warn("Could not record teleconsult session start for referral={}: {}", id, e.getMessage());
            }
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
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.PROVIDER_ID, required = false) String providerId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            // Carry the acceptance context (receiving facility, planned review time, notes)
            // through to PCT so it is persisted on the referral — not just the acceptor.
            Map<String, Object> acceptance = new LinkedHashMap<>();
            acceptance.put("accepted_by", actorId != null ? actorId : "unknown");
            if (body != null) {
                copyIfPresent(body, acceptance, "receiving_facility_id", "receivingFacilityId");
                copyIfPresent(body, acceptance, "receiving_facility_name", "receivingFacilityName");
                copyIfPresent(body, acceptance, "scheduled_at", "scheduledAt");
                copyIfPresent(body, acceptance, "notes", "note");
            }
            // TM-B8 authority gate: resolve the accepting provider's VARAPI standing and attach
            // the evidence. PCT is the authoritative enforcement point (shadow-then-enforce).
            acceptance.put("authority", resolveAcceptAuthority(providerId, actorId));
            var accepted = pctClient.acceptReferral(id, acceptance);
            emitTelemedicineNotification("TELECONSULT_ACCEPTED", accepted, actorId, "Teleconsult request accepted.");
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_REFERRAL_ACCEPTED", "POST:teleconsult/accept", "SUCCESS",
                    actorId, "PROVIDER", extractPatient(accepted), "TeleconsultReferral",
                    id, Map.of());
            return ok(accepted, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /** TM-B1: guard-permitted next actions for the referral's current state (drives contextual UI). */
    @GetMapping("/sessions/{id}/allowed-actions")
    public ResponseEntity<Map<String, Object>> allowedActions(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(pctClient.allowedActions(id), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /**
     * TM-B19: per-session failure diagnostics for the helpdesk/ops surface. Combines the rtc
     * session-events timeline with participant media aggregates. NO clinical content by
     * construction — both sources are transport telemetry (event types, identities, durations,
     * disconnect reasons); the response never touches the referral's clinical fields.
     */
    @GetMapping("/ops/session-diagnostics/{id}")
    public ResponseEntity<Map<String, Object>> sessionDiagnostics(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sessionId", id);
            try {
                out.put("events", pickEvents(rtcClient.getSessionEvents(id, 200)));
            } catch (Exception ex) {
                out.put("events", java.util.List.of());
                out.put("eventsError", "RTC_EVENTS_UNAVAILABLE");
            }
            try {
                out.put("stats", rtcClient.getSessionStats(id));
            } catch (Exception ex) {
                out.put("stats", null);
                out.put("statsError", "RTC_STATS_UNAVAILABLE");
            }
            return ok(out, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("RTC_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    private Object pickEvents(JsonNode eventsPayload) {
        if (eventsPayload == null) return java.util.List.of();
        JsonNode events = eventsPayload.path("events");
        return events.isArray() ? events : eventsPayload;
    }

    /**
     * TM-B15 (B15-1): MDT / specialist-board proxies. The board is a pct-owned clinical record;
     * identity visibility (pseudonymised presentation) is enforced server-side in pct — the viewer
     * identity forwarded here is what pct's read model pseudonymises against.
     */
    @PostMapping("/mdt/sessions")
    public ResponseEntity<Map<String, Object>> mdtCreateSession(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            Map<String, Object> req = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
            if (actorId != null && !actorId.isBlank() && !req.containsKey("chair_id")) {
                req.put("chair_id", actorId);
            }
            return ok(pctClient.mdtCreateSession(req), requestId, correlationId, HttpStatus.CREATED);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    @GetMapping("/mdt/sessions/{id}")
    public ResponseEntity<Map<String, Object>> mdtBoard(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            return ok(pctClient.mdtGetBoard(id, actorId), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    @PostMapping("/mdt/sessions/{id}/cases")
    public ResponseEntity<Map<String, Object>> mdtAddCase(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            return ok(pctClient.mdtAddCase(id, body), requestId, correlationId, HttpStatus.CREATED);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    @PostMapping("/mdt/cases/{caseItemId}/outcome")
    public ResponseEntity<Map<String, Object>> mdtOutcome(
            @PathVariable String caseItemId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            Map<String, Object> req = new LinkedHashMap<>(body);
            if (actorId != null && !actorId.isBlank() && !req.containsKey("recorded_by")) {
                req.put("recorded_by", actorId);
            }
            return ok(pctClient.mdtRecordOutcome(caseItemId, req), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /**
     * TM-B5 (B5-4): consent withdrawn mid-session. PCT records the authoritative truth (consent
     * WITHDRAWN + media rung forced to ASYNC); this endpoint then makes a best-effort rtc-gateway
     * end to tear the live room down server-side, surfacing mediaStopped honestly (the client also
     * drops media immediately on confirmation — the fastest stop). Case continues on the durable chat.
     */
    @PostMapping("/sessions/{id}/consent-withdraw")
    public ResponseEntity<Map<String, Object>> consentWithdraw(
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
            Map<String, Object> reqBody = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
            if (actorId != null && !actorId.isBlank() && !reqBody.containsKey("responder_id")) {
                reqBody.put("responder_id", actorId);
            }
            JsonNode result = pctClient.lifecycleAction(id, "consent-withdraw", reqBody);
            // Best-effort server-side media stop — never fails the withdrawal (the recorded truth has
            // already committed in PCT); the outcome is surfaced honestly so the UI never implies the
            // room was torn down when it was not.
            boolean mediaStopped = false;
            try {
                rtcClient.endSession(id);
                mediaStopped = true;
            } catch (Exception rtcEx) {
                log.warn("Consent-withdraw {}: rtc end best-effort failed ({})", id, rtcEx.getMessage());
            }
            if (result != null && result.isObject()) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) result).put("mediaStopped", mediaStopped);
            }
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_CONSENT_WITHDRAWN", "POST:teleconsult/consent-withdraw", "SUCCESS",
                    actorId, "PATIENT", extractPatient(result), "TeleconsultReferral", id,
                    Map.of("mediaStopped", String.valueOf(mediaStopped)));
            return ok(result, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /**
     * TM-B5 (B5-3): labelled provider-only side-channel — provider-to-provider discussion that is
     * NEVER visible to the patient (stored in a separate pct column, off every patient-facing read).
     * These endpoints are provider-facing (governed mutate/read); the citizen telehealth accessors
     * do not expose provider_notes.
     */
    @PostMapping("/sessions/{id}/provider-note")
    public ResponseEntity<Map<String, Object>> addProviderNote(
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
            String note = val(body, "note", "message", "text");
            if (note == null || note.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "INVALID_NOTE", "note is required", requestId, correlationId);
            }
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("note", note);
            if (actorId != null && !actorId.isBlank()) req.put("author", actorId);
            String authorName = val(body, "authorName", "author_name");
            if (authorName != null && !authorName.isBlank()) req.put("authorName", authorName);
            JsonNode result = pctClient.lifecycleAction(id, "provider-note", req);
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_PROVIDER_NOTE_ADDED", "POST:teleconsult/provider-note", "SUCCESS",
                    actorId, "PROVIDER", null, "TeleconsultReferral", id, Map.of());
            return ok(result, requestId, correlationId, HttpStatus.ACCEPTED);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    @GetMapping("/sessions/{id}/provider-notes")
    public ResponseEntity<Map<String, Object>> getProviderNotes(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            JsonNode result = pctClient.getProviderNotes(id);
            return ok(result, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /**
     * TM-B5 (B5-2): media-modality downgrade ladder VIDEO -> AUDIO -> ASYNC. On media failure or
     * bandwidth loss the client (or provider) steps the session down a rung; ASYNC drops live media
     * and continues on the durable case-persisted message thread. Monotonic in PCT (restore=true to
     * step up). The step + reason are recorded on the case and emit a versioned lifecycle event.
     */
    @PostMapping("/sessions/{id}/media-downgrade")
    public ResponseEntity<Map<String, Object>> mediaDowngrade(
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
            Map<String, Object> reqBody = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
            if (actorId != null && !actorId.isBlank() && !reqBody.containsKey("responder_id")) {
                reqBody.put("responder_id", actorId);
            }
            JsonNode result = pctClient.lifecycleAction(id, "media-modality", reqBody);
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_MEDIA_DOWNGRADE", "POST:teleconsult/media-downgrade", "SUCCESS",
                    actorId, "PROVIDER", extractPatient(result), "TeleconsultReferral", id,
                    Map.of("toModality", String.valueOf(reqBody.getOrDefault("modality", ""))));
            return ok(result, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /** TM-B1: reason-bound lifecycle transitions. Cancel/reopen/escalate/transfer. */
    @PostMapping("/sessions/{id}/{action:cancel|reopen|escalate|transfer|error-mark}")
    public ResponseEntity<Map<String, Object>> lifecycleAction(
            @PathVariable String id,
            @PathVariable String action,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            Map<String, Object> reqBody = body == null ? Map.of() : body;
            JsonNode result = pctClient.lifecycleAction(id, action, reqBody);
            // TM-B12 emergency side effects: the PCT state transition is the clinical source of
            // truth and has already committed — the dispatch below is best-effort so a downstream
            // outage never blocks the escalation, but its success/failure is surfaced honestly
            // (emergencyDispatched/transferDispatched + ref) so the console never implies EMS/transport
            // was summoned when it was not.
            if ("escalate".equals(action)) {
                attachEmergencyDispatch(result, id, reqBody, tenantId, actorId, facilityId);
            } else if ("transfer".equals(action)) {
                attachTransferDispatch(result, id, reqBody, tenantId, actorId, facilityId);
            }
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_REFERRAL_" + action.toUpperCase(Locale.ROOT).replace('-', '_'),
                    "POST:teleconsult/" + action, "SUCCESS",
                    actorId, "PROVIDER", extractPatient(result), "TeleconsultReferral", id, Map.of());
            return ok(result, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /**
     * TM-B12: on escalation, raise a Daidzai emergency incident so EMS / emergency response is
     * actually summoned (not just a state flag). Best-effort — never fails the transition; the
     * outcome is written back onto the referral payload so the UI can confirm or prompt a manual call.
     */
    private void attachEmergencyDispatch(JsonNode result, String referralId, Map<String, Object> body,
                                         String tenantId, String actorId, String facilityId) {
        if (!(result instanceof ObjectNode node)) return;
        try {
            String patient = extractPatient(result);
            Map<String, Object> incident = new java.util.LinkedHashMap<>();
            incident.put("emergencyCategory", defaultString(val(body, "emergencyCategory", "emergency_category"), "MEDICAL"));
            incident.put("severity", defaultString(val(body, "severity"), "HIGH"));
            incident.put("description", defaultString(val(body, "reason"), "Teleconsult clinical escalation " + referralId));
            incident.put("facilityId", facilityId);
            incident.put("subjectIdentityMode", defaultString(val(body, "subjectIdentityMode", "subject_identity_mode"), "IDENTIFIED"));
            incident.put("subjectCpid", patient);
            incident.put("locationDescription", val(body, "locationDescription", "location_description"));
            putIfNumber(incident, "lat", body.get("lat"));
            putIfNumber(incident, "lng", body.get("lng"));
            JsonNode created = daidzaiClient.createIncident(incident);
            String incidentId = firstText(created, "id", "incident_id", "incidentId");
            node.put("emergencyDispatched", incidentId != null && !incidentId.isBlank());
            if (incidentId != null) node.put("emergencyIncidentId", incidentId);
        } catch (Exception e) {
            log.error("Teleconsult {} escalation raised state but Daidzai incident FAILED — EMS NOT dispatched: {}",
                    referralId, e.getMessage());
            node.put("emergencyDispatched", false);
        }
    }

    /**
     * TM-B12: on transfer, open a Nhume delivery for the patient/case transport (Ndila destination
     * passed through when supplied). Best-effort with the same honesty contract as escalation.
     */
    private void attachTransferDispatch(JsonNode result, String referralId, Map<String, Object> body,
                                        String tenantId, String actorId, String facilityId) {
        if (!(result instanceof ObjectNode node)) return;
        try {
            String destFacility = val(body, "destinationFacilityId", "destination_facility_id", "toFacilityId");
            String ndila = val(body, "ndilaDestination", "ndila_destination", "destination");
            String caseRef = "TeleconsultReferral/" + referralId;
            // Nhume CreateDeliveryRequest is snake_case; request_source is @NotBlank.
            Map<String, Object> delivery = new java.util.LinkedHashMap<>();
            delivery.put("request_source", "TELECONSULT");
            delivery.put("delivery_type", "PATIENT_TRANSFER");
            delivery.put("priority", "URGENT");
            delivery.put("requesting_actor_id", actorId);
            delivery.put("requesting_actor_type", "PROVIDER");
            delivery.put("requesting_facility_id", facilityId);
            delivery.put("telehealth_session_ref", caseRef);
            delivery.put("clinical_context_ref", caseRef);
            delivery.put("notes", defaultString(val(body, "reason"), "Teleconsult transfer " + referralId));
            delivery.put("submit_immediately", true);
            if (facilityId != null) {
                delivery.put("origin", Map.of("kind", "FACILITY", "ref", facilityId));
            }
            if (destFacility != null || ndila != null) {
                Map<String, Object> dest = new java.util.LinkedHashMap<>();
                dest.put("kind", "FACILITY");
                if (destFacility != null) dest.put("ref", destFacility);
                if (ndila != null) dest.put("label", ndila);
                delivery.put("destination", dest);
            }
            String patient = extractPatient(result);
            if (patient != null) {
                delivery.put("recipient", Map.of("kind", "PATIENT", "ref", patient));
            }
            JsonNode created = nhumeClient.createDelivery(delivery);
            String deliveryId = firstText(created, "delivery_id", "deliveryId", "id");
            node.put("transferDispatched", deliveryId != null && !deliveryId.isBlank());
            if (deliveryId != null) node.put("transferDeliveryId", deliveryId);
        } catch (Exception e) {
            log.error("Teleconsult {} transfer raised state but Nhume delivery FAILED — transport NOT arranged: {}",
                    referralId, e.getMessage());
            node.put("transferDispatched", false);
        }
    }

    /** First non-blank text value among the given JSON fields (handles snake/camel id variants). */
    private String firstText(JsonNode node, String... fields) {
        if (node == null) return null;
        for (String f : fields) {
            if (node.hasNonNull(f)) {
                String v = node.get(f).asText();
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    private void putIfNumber(Map<String, Object> target, String key, Object value) {
        if (value instanceof Number n) {
            target.put(key, n);
        } else if (value instanceof String s && !s.isBlank()) {
            try { target.put(key, Double.parseDouble(s.trim())); } catch (NumberFormatException ignored) { /* skip */ }
        }
    }

    /**
     * TM-B7: place a diagnostic/clinical order from within a teleconsult session. The order is
     * stamped with TELECONSULT provenance (source_ref = referral/session id) so the fulfilment +
     * result-return trail links back to the virtual-care case; OROS rejects a duplicate active
     * order for the same referral + coded item (409 CONFLICT, surfaced verbatim by A0 passthrough).
     */
    @PostMapping("/sessions/{id}/orders")
    public ResponseEntity<Map<String, Object>> placeSessionOrder(
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
            String orderType = val(body, "orderType", "order_type");
            String priority = val(body, "priority");
            String patientCpid = val(body, "patientCpid", "patient_cpid");
            String ziboCode = val(body, "ziboOrderCode", "zibo_order_code");
            String clinicalNotes = val(body, "clinicalNotes", "clinical_notes");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = body.get("items") instanceof List
                    ? (List<Map<String, Object>>) body.get("items") : null;
            JsonNode result = orosClient.placeTeleconsultOrder(
                    id, orderType, priority, patientCpid, ziboCode, clinicalNotes, items);
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_ORDER_PLACED", "POST:teleconsult/orders", "SUCCESS",
                    actorId, "PROVIDER", patientCpid, "TeleconsultReferral", id, Map.of());
            return ok(result, requestId, correlationId, HttpStatus.CREATED);
        } catch (Exception e) {
            return upstreamFailure("OROS_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /** TM-B7: list tasks linked to a teleconsult session (referral). */
    @GetMapping("/sessions/{id}/tasks")
    public ResponseEntity<Map<String, Object>> listSessionTasks(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            return ok(pctClient.listReferralTasks(id), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /** TM-B7: create a follow-up / execution task scoped to a teleconsult session (referral). */
    @PostMapping("/sessions/{id}/tasks")
    public ResponseEntity<Map<String, Object>> addSessionTask(
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
            JsonNode result = pctClient.addReferralTask(id, body == null ? Map.of() : body);
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_TASK_CREATED", "POST:teleconsult/tasks", "SUCCESS",
                    actorId, "PROVIDER", extractPatient(result), "TeleconsultReferral", id, Map.of());
            return ok(result, requestId, correlationId, HttpStatus.CREATED);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
        return accept(id, requestId, correlationId, tenantId, purposeOfUse, facilityId, actorId, null, null);
    }

    private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String toKey, String altKey) {
        Object v = from.get(toKey);
        if (v == null) {
            v = from.get(altKey);
        }
        if (v != null && !String.valueOf(v).isBlank()) {
            to.put(toKey, v);
        }
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
            // A decline reason is mandatory — silently defaulting it would leave the
            // audit trail asserting a reason the specialist never gave.
            if (reason == null || reason.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "DECLINE_REASON_REQUIRED",
                        "A reason is required to decline a teleconsult.", requestId, correlationId);
            }
            Map<String, Object> decline = new LinkedHashMap<>();
            decline.put("response_type", "DECLINED");
            decline.put("status", "DECLINED");
            decline.put("message", reason);
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
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /**
     * TM-B6: structured response (v1 spine + v2 additive sections). The provider console posts here
     * when it captures coded diagnosis / prescriptions / patient instructions / onward referral —
     * the enriched record the FHIR Composition + DiagnosticReport project and the patient timeline reads.
     */
    @PostMapping("/sessions/{id}/response-structured")
    public ResponseEntity<Map<String, Object>> submitStructuredResponse(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            telemedicineGovernanceService.assertGovernedMutate();
            Map<String, Object> reqBody = body == null ? Map.of() : body;
            // TM-B6 pre-submission validation (terminology + prescribing authority + allergy) — runs
            // BEFORE the response is filed. In ENFORCE a response carrying an ERROR is blocked (422);
            // in SHADOW the issues are recorded and attached so the provider still sees them.
            String patientCpid = val(reqBody, "patientCpid", "patient_cpid", "patientId", "patient_id");
            var validation = responseValidationService.validate(reqBody, actorId, patientCpid);
            if (validation.blocks()) {
                Map<String, Object> errBody = new java.util.LinkedHashMap<>();
                errBody.put("error", Map.of("code", "RESPONSE_VALIDATION_FAILED",
                        "message", "This response has issues that must be resolved before it can be filed."));
                errBody.put("validation", objectMapper.convertValue(responseValidationService.toJson(validation), Map.class));
                errBody.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errBody);
            }
            var response = pctClient.respondReferralStructured(id, reqBody);
            if (response == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No consultation response payload returned", requestId, correlationId);
            }
            if (response instanceof ObjectNode obj) {
                obj.set("preSubmissionValidation", responseValidationService.toJson(validation));
            }
            return ok(response, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
            boolean clinicalSummaryWritten = writeTeleconsultSummaryToFhir(
                    id, completed, actorId, tenantId, correlationId, purposeOfUse);
            // The PCT referral completion is the durable clinical record; the FHIR
            // DiagnosticReport is a secondary projection. Surface whether it was
            // written so completion never silently claims a clinical summary exists
            // when fhir-gateway dropped it — the UI can prompt a retry.
            if (completed instanceof ObjectNode completedObj) {
                completedObj.put("clinicalSummaryWritten", clinicalSummaryWritten);
            }
            triggerTeleconsultBilling(id, completed, body);
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_REFERRAL_COMPLETED", "POST:teleconsult/complete", "SUCCESS",
                    actorId, "PROVIDER", extractPatient(completed), "TeleconsultReferral",
                    id, Map.of("breakGlass", Boolean.parseBoolean(val(body, "breakGlassOverride", "break_glass_override"))));
            return ok(completed, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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

    /**
     * Virtual-hospital pool queue (Lane E Wave 1): governed read of PCT's pool-scoped
     * teleconsult worklist — referrals routed to a specialty pool (oldest first). Any
     * telemedicine-authorized provider may view/accept; per-pool duty rosters are Wave 2.
     */
    @GetMapping("/pool/{poolId}/queue")
    public ResponseEntity<Map<String, Object>> poolQueue(
            @PathVariable String poolId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.PURPOSE_OF_USE, required = false) String purposeOfUse,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            telemedicineGovernanceService.assertGovernedRead();
            JsonNode rows = pctClient.listPoolReferrals(poolId, status, page, Math.min(Math.max(size, 1), 100));
            if (rows == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No pool queue payload returned", requestId, correlationId);
            }
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_POOL_QUEUE_VIEWED", "GET:teleconsult/pool/queue", "SUCCESS",
                    actorId, "PROVIDER", null, "TeleconsultPool", poolId,
                    Map.of("status", defaultString(status, "SUBMITTED")));
            return ok(normalizeReferralPayload(rows), requestId, correlationId, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            HttpStatus resolved = HttpStatus.resolve(e.getStatusCode().value());
            return error(resolved == null ? HttpStatus.FORBIDDEN : resolved,
                    "TELEMEDICINE_GOVERNANCE_INVALID",
                    e.getReason() == null ? "Telemedicine governance rejected request" : e.getReason(),
                    requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
    }

    /**
     * Route (or re-route) a teleconsult referral to a team/pool (HO-1 closure): governed
     * proxy of PCT's Stage-3 {@code POST /v1/referrals/{id}/route}.
     */
    @PostMapping("/sessions/{id}/route")
    public ResponseEntity<Map<String, Object>> routeSession(
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
            String poolId = val(body, "routingPoolId", "routing_pool_id", "poolId", "pool_id");
            if (poolId == null || poolId.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "MISSING_ROUTING_POOL",
                        "routingPoolId is required to route a teleconsult to a pool", requestId, correlationId);
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("routing_kind", defaultString(val(body, "routingKind", "routing_kind"), "POOL"));
            payload.put("routing_pool_id", poolId);
            String rosterId = val(body, "onCallRosterId", "on_call_roster_id");
            if (rosterId != null && !rosterId.isBlank()) {
                payload.put("on_call_roster_id", rosterId);
            }
            JsonNode routed = pctClient.routeReferral(id, payload);
            if (routed == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No referral route payload returned", requestId, correlationId);
            }
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, purposeOfUse, facilityId,
                    "TELEMEDICINE_REFERRAL_ROUTED", "POST:teleconsult/route", "SUCCESS",
                    actorId, "PROVIDER", extractPatient(routed), "TeleconsultReferral",
                    id, Map.of("poolId", poolId));
            return ok(normalizeReferralPayload(routed), requestId, correlationId, HttpStatus.OK);
        } catch (ResponseStatusException e) {
            HttpStatus resolved = HttpStatus.resolve(e.getStatusCode().value());
            return error(resolved == null ? HttpStatus.FORBIDDEN : resolved,
                    "TELEMEDICINE_GOVERNANCE_INVALID",
                    e.getReason() == null ? "Telemedicine governance rejected request" : e.getReason(),
                    requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
        }
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
            return upstreamFailure("VARAPI_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("TUSO_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("TUSO_UNAVAILABLE", e, requestId, correlationId);
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
        billingContextService.applyBillingContext(payload, patientCpid, facilityRef, body);
    }

    /**
     * TM-B8: resolve the accepting provider's VARAPI standing (derived {@code status} axis:
     * ACTIVE / SUSPENDED / REVOKED) as authority evidence for PCT's accept gate. Best-effort —
     * a VARAPI outage or unresolved provider yields {@code resolved:false} (PCT records it in
     * shadow; only ENFORCE blocks). Never throws.
     */
    private Map<String, Object> resolveAcceptAuthority(String providerId, String actorId) {
        Map<String, Object> authority = new LinkedHashMap<>();
        String id = firstNonBlank(providerId, actorId);
        authority.put("checker", "experience-bff");
        authority.put("providerId", id);
        if (id == null || id.isBlank()) {
            authority.put("resolved", false);
            authority.put("status", "UNKNOWN");
            return authority;
        }
        try {
            JsonNode provider = varapiClient.getProvider(id);
            if (provider == null || provider.isNull() || !provider.hasNonNull("status")) {
                authority.put("resolved", false);
                authority.put("status", "UNKNOWN");
            } else {
                authority.put("resolved", true);
                authority.put("status", provider.get("status").asText("UNKNOWN"));
            }
        } catch (Exception e) {
            log.warn("Provider authority resolution failed for {}: {}", id, e.getMessage());
            authority.put("resolved", false);
            authority.put("status", "UNKNOWN");
        }
        return authority;
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

    /**
     * Upstream-error passthrough (A0, TM-B1 prerequisite). Domain rejections from
     * sovereign services (4xx: ILLEGAL_TRANSITION, CONSENT_REQUIRED_MISSING,
     * PROVIDER_NOT_AUTHORIZED, …) must reach clients with their real status and
     * code — masking them as 502 PCT_UNAVAILABLE turns governed rejections into
     * fake outages. 5xx and transport failures keep the upstream-failure shape.
     */
    private ResponseEntity<Map<String, Object>> upstreamFailure(String code, Exception e, String requestId, String correlationId) {
        // A trust decision made HERE is subject to the same principle this method already states
        // for upstream 4xx: masking a governed refusal as an outage is a lie about its cause, and
        // an outage is exactly what PCT_UNAVAILABLE claims. Rethrow so the advice renders the
        // canonical challenge -- reason code, permitted next step, continuation -- instead of a
        // 502 that tells the user their consent problem is a downstream service being down.
        //
        // Fixed here rather than at each `catch (Exception)` because this controller has 53 of
        // them around 39 governance calls; one shared exit is the only tractable seam.
        if (e instanceof zw.gov.mohcc.impilo.experience.trust.TrustChallengeException challenge) {
            throw challenge;
        }
        if (e instanceof org.springframework.web.client.HttpStatusCodeException http
                && http.getStatusCode().is4xxClientError()) {
            String upstreamCode = code;
            String upstreamMessage = http.getMessage();
            try {
                JsonNode body = objectMapper.readTree(http.getResponseBodyAsString());
                // PCT envelope: {success:false, error:{code,message,status}}; generic: {error:{code,message}}
                JsonNode err = body.path("error");
                if (err.hasNonNull("code")) {
                    upstreamCode = err.path("code").asText(upstreamCode);
                }
                if (err.hasNonNull("message")) {
                    upstreamMessage = err.path("message").asText(upstreamMessage);
                }
            } catch (Exception parseFailure) {
                log.debug("Upstream 4xx body not parseable, forwarding status only: {}", parseFailure.getMessage());
            }
            log.info("Teleconsult upstream domain rejection passed through: {} {}", http.getStatusCode(), upstreamCode);
            return error(HttpStatus.valueOf(http.getStatusCode().value()), upstreamCode, upstreamMessage, requestId, correlationId);
        }
        return upstreamFailure(code, e.getMessage(), requestId, correlationId);
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
        if (POOL_ROUTING_TYPES.contains(routingType)) {
            // TM-B3: the POOL target is a virtual-pool id resolved against the Vashandi virtual-pool
            // directory (formerly a 501 stub). The pool queue itself is materialised in PCT; here we
            // only confirm the pool exists so a referral cannot be routed into a non-existent pool.
            String poolId = routingTarget.trim();
            if (poolId.length() < 2) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        routingType + " routing target must identify a virtual pool");
            }
            try {
                JsonNode onDuty = vashandiClient.getVirtualPoolOnDuty(poolId);
                if (onDuty == null || onDuty.isNull()) {
                    return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                            "Virtual pool '" + poolId + "' was not found in the pool directory");
                }
                return null;
            } catch (HttpClientErrorException.NotFound notFound) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "Virtual pool '" + poolId + "' was not found in the pool directory");
            } catch (Exception e) {
                // Fail-open: a directory outage must not block routing (same posture as the accept-time
                // on-duty resolution, which records UNKNOWN rather than blocking).
                log.warn("Vashandi pool directory lookup failed for pool {}: {} — allowing routing", poolId, e.getMessage());
                return null;
            }
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
            return upstreamFailure("RTC_GATEWAY_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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
            return upstreamFailure("PCT_UNAVAILABLE", e, requestId, correlationId);
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

    /**
     * Projects the completed teleconsult as a FHIR DiagnosticReport. Returns
     * whether the write succeeded so the caller can report an honest
     * clinical-summary status instead of silently dropping it on a
     * fhir-gateway outage. (A durable retry belongs in a sovereign service —
     * the BFF is stateless; failures are logged at ERROR for reconciliation.)
     */
    private static final String TELECONSULT_REFERRAL_SYSTEM = "http://impilo.mohcc.gov.zw/fhir/teleconsult-referral";

    /**
     * Project a completed teleconsult to the SHR (TM-B6): a virtual-class {@code Encounter}, the
     * {@code DiagnosticReport} summary, and a response {@code Composition} document — each routed
     * through the governed gateway forward endpoint (consent PEP → route → audit + outbox), each
     * carrying the referral id as a business identifier so the three link back to the case.
     *
     * <p>Honesty contract: returns true only when the SHR actually received the clinical record —
     * i.e. the DiagnosticReport AND Composition both delivered (the Encounter is best-effort
     * context). Any gateway non-SUCCESS (NO_ROUTE/FORWARD_FAILED/CONSENT_DENIED/auth) → false, so
     * completion never falsely claims a clinical summary reached the SHR.</p>
     */
    private boolean writeTeleconsultSummaryToFhir(String referralId, JsonNode completed, String actorId,
                                                  String tenantId, String correlationId, String purposeOfUse) {
        try {
            String patientRef = extractPatient(completed);
            if (patientRef == null || patientRef.isBlank()) {
                log.warn("Teleconsult {} completed without a patient reference — no FHIR summary written", referralId);
                return false;
            }
            String now = OffsetDateTime.now().toString();
            String conclusion = teleconsultConclusion(referralId, completed);

            // 1. Virtual-class Encounter — the context the summary + composition belong to.
            ObjectNode encounter = objectMapper.createObjectNode();
            encounter.put("resourceType", "Encounter");
            encounter.put("status", "finished");
            ObjectNode encClass = encounter.putObject("class");
            encClass.put("system", "http://terminology.hl7.org/CodeSystem/v3-ActCode");
            encClass.put("code", "VR");
            encClass.put("display", "virtual");
            encounter.putObject("subject").put("reference", "Patient/" + patientRef);
            encounter.putObject("period").put("end", now);
            encounter.putArray("identifier").addObject()
                    .put("system", TELECONSULT_REFERRAL_SYSTEM).put("value", referralId);
            boolean encounterOk = forwardFhirResource("Encounter", encounter, referralId, patientRef,
                    tenantId, correlationId, actorId, purposeOfUse);

            // 2. DiagnosticReport — the coded teleconsult summary.
            ObjectNode report = objectMapper.createObjectNode();
            report.put("resourceType", "DiagnosticReport");
            report.put("status", "final");
            ObjectNode code = report.putObject("code");
            code.putArray("coding").addObject()
                    .put("system", "http://impilo.mohcc.gov.zw/fhir/CodeSystem/teleconsult")
                    .put("code", "teleconsult-summary")
                    .put("display", "Teleconsultation summary");
            code.put("text", "Teleconsultation summary");
            report.putObject("subject").put("reference", "Patient/" + patientRef);
            report.put("effectiveDateTime", now);
            report.put("issued", now);
            report.put("conclusion", conclusion);
            report.putArray("performer").addObject().put("display", defaultString(actorId, "unknown"));
            report.putArray("identifier").addObject()
                    .put("system", TELECONSULT_REFERRAL_SYSTEM).put("value", referralId);
            boolean reportOk = forwardFhirResource("DiagnosticReport", report, referralId, patientRef,
                    tenantId, correlationId, actorId, purposeOfUse);

            // 3. Composition — the response document (what a clinician reads as the note of record).
            ObjectNode composition = objectMapper.createObjectNode();
            composition.put("resourceType", "Composition");
            composition.put("status", "final");
            ObjectNode compType = composition.putObject("type");
            compType.putArray("coding").addObject()
                    .put("system", "http://impilo.mohcc.gov.zw/fhir/CodeSystem/teleconsult")
                    .put("code", "teleconsult-summary")
                    .put("display", "Teleconsultation summary");
            compType.put("text", "Teleconsultation summary");
            composition.putObject("subject").put("reference", "Patient/" + patientRef);
            composition.put("date", now);
            composition.putArray("author").addObject().put("display", defaultString(actorId, "unknown"));
            composition.put("title", "Teleconsultation Summary");
            composition.putObject("identifier")
                    .put("system", TELECONSULT_REFERRAL_SYSTEM).put("value", referralId);
            ObjectNode section = composition.putArray("section").addObject();
            section.put("title", "Assessment & Plan");
            ObjectNode text = section.putObject("text");
            text.put("status", "generated");
            text.put("div", "<div xmlns=\"http://www.w3.org/1999/xhtml\">" + xhtmlEscape(conclusion) + "</div>");
            boolean compositionOk = forwardFhirResource("Composition", composition, referralId, patientRef,
                    tenantId, correlationId, actorId, purposeOfUse);

            if (!encounterOk) {
                log.warn("Teleconsult {} Encounter projection not delivered (context best-effort)", referralId);
            }
            // The clinical record of truth = DiagnosticReport + Composition. Both must land.
            return reportOk && compositionOk;
        } catch (Exception ex) {
            log.error("Teleconsult FHIR writeback FAILED for {} — clinical summary NOT recorded: {}",
                    referralId, ex.getMessage());
            return false;
        }
    }

    /** Forward one FHIR resource through the governed gateway; true iff outcome == SUCCESS (delivered). */
    private boolean forwardFhirResource(String resourceType, ObjectNode resource, String referralId,
                                        String patientRef, String tenantId, String correlationId,
                                        String actorId, String purposeOfUse) {
        try {
            JsonNode fwd = fhirGatewayClient.forward(tenantId, correlationId, actorId,
                    resourceType, "CREATE", objectMapper.writeValueAsString(resource), patientRef, purposeOfUse);
            String outcome = fwd == null ? null : fwd.path("data").path("outcome").asText(null);
            boolean delivered = "SUCCESS".equals(outcome);
            if (!delivered) {
                log.warn("Teleconsult {} {} NOT delivered — gateway outcome={}", referralId, resourceType, outcome);
            }
            return delivered;
        } catch (Exception ex) {
            log.error("Teleconsult {} {} forward FAILED: {}", referralId, resourceType, ex.getMessage());
            return false;
        }
    }

    /** Minimal XHTML escaping for FHIR narrative div content. */
    private String xhtmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Prefer a real conclusion from the structured response; fall back to a neutral completion note. */
    private String teleconsultConclusion(String referralId, JsonNode completed) {
        if (completed != null) {
            for (String path : new String[] {"structuredResponse", "completion"}) {
                JsonNode node = completed.path(path);
                for (String field : new String[] {"conclusion", "assessment", "closureNarrative", "summary"}) {
                    JsonNode v = node.path(field);
                    if (v.isTextual() && !v.asText().isBlank()) return v.asText();
                }
            }
        }
        return "Teleconsult referral " + referralId + " completed.";
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
     * Scheduled teleconsult: create the booking-service appointment (type TELECONSULT, linked via
     * the dedicated {@code external_ref} column — booking V002; the {@code notes} tag remains for
     * back-compat readers), write the appointment linkage back onto the referral, and send the
     * booking confirmation notification.
     *
     * <p>Formerly-stale seams, now closed (TM-B4): PCT has real {@code appointment_id}/
     * {@code scheduled_at} columns (V032), and notification-service supports {@code notBefore}
     * scheduling (V016) — T-minus reminders ride {@code telemedicine.session.scheduled}/
     * {@code .rescheduled} through TelemedicineCommsWorkflowService.</p>
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
            // TM-B4: dedicated machine linkage (booking V002) — notes tag kept for back-compat reads.
            appointment.put("external_ref", "teleconsult:referral:" + referralId);
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

    /**
     * Teleconsult referrals → the {@code TelemedicineSession} rows the shell declares.
     *
     * <p>A teleconsult session is modelled as a PCT referral — {@code createSession} creates one —
     * so listing referrals here is the design, not a mix-up. What was wrong is the shape: the rows
     * went out as raw {@code ReferralPackageEntity} objects while {@code useTelemedicine} declares
     * {@code attributes.status}, so every consumer that read a session threw.
     *
     * <p>{@code room_url} is deliberately null. A joinable room URL is minted per join, with a
     * token, by {@code /sessions/&#123;id&#125;/media/token}. Putting a durable one in a list
     * payload would hand every reader of the list a way into the consultation, so its absence here
     * is a property to keep rather than a gap to fill.
     *
     * <p>{@code scheduled_at} and {@code started_at} come from PCT's session lifecycle columns
     * (V500), set on the waiting-room admission. They used to have no source at all, and were left
     * null rather than filled from {@code submittedAt} — which would have reported the referral's
     * paperwork time as the consultation's start, on a screen a clinician reads to know whether a
     * consult is under way. {@code duration_seconds} is derived and stays null until both ends are
     * known, because a duration for an unstarted consult is a number where "it has not happened"
     * is the honest answer.
     */
    private Object sessionRows(Object payload) {
        if (!(payload instanceof JsonNode node)) {
            return payload;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode referral : zw.gov.mohcc.impilo.experience.support.JsonApiRows.items(node)) {
            Map<String, Object> attributes =
                    zw.gov.mohcc.impilo.experience.support.JsonApiRows.attributesOf(referral);

            sessionAlias(attributes, referral, "encounterId", "encounter_id");
            sessionAlias(attributes, referral, "patientCpid", "patient_id");
            sessionAlias(attributes, referral, "providerId", "provider_id");
            sessionAlias(attributes, referral, "facilityId", "facility_id");
            sessionAlias(attributes, referral, "referralPackageStatus", "status");
            sessionAlias(attributes, referral, "completedAt", "ended_at");
            sessionAlias(attributes, referral, "sessionStartedAt", "started_at");
            sessionAlias(attributes, referral, "sessionScheduledAt", "scheduled_at");
            sessionAlias(attributes, referral, "referralId", "referral_id");
            sessionAlias(attributes, referral, "createdAt", "created_at");
            sessionAlias(attributes, referral, "updatedAt", "updated_at");
            sessionAlias(attributes, referral, "clinicalQuestion", "notes");

            // A virtual consult's mode is what "session type" means on this screen.
            String sessionType = referral.hasNonNull("virtualMode")
                    ? referral.get("virtualMode").asText()
                    : (referral.hasNonNull("modality") ? referral.get("modality").asText() : null);
            attributes.put("session_type", sessionType);
            attributes.put("sessionType", sessionType);

            attributes.putIfAbsent("room_url", null);
            attributes.putIfAbsent("scheduled_at", null);
            attributes.putIfAbsent("started_at", null);
            attributes.put("duration_seconds", durationSeconds(referral));

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", zw.gov.mohcc.impilo.experience.support.JsonApiRows.text(referral, "referralId"));
            row.put("type", "TelemedicineSession");
            row.put("attributes", attributes);
            rows.add(row);
        }
        return rows;
    }

    /**
     * Elapsed consultation time, or null while it is still running or was never joined. A duration
     * is only meaningful once both ends are known — reporting one for an unstarted consult would
     * put a number where the honest answer is "it has not happened".
     */
    private static Long durationSeconds(JsonNode referral) {
        String started = zw.gov.mohcc.impilo.experience.support.JsonApiRows.text(referral, "sessionStartedAt");
        String ended = zw.gov.mohcc.impilo.experience.support.JsonApiRows.text(referral, "completedAt");
        if (started == null || ended == null) {
            return null;
        }
        try {
            return java.time.Duration.between(
                    java.time.OffsetDateTime.parse(started),
                    java.time.OffsetDateTime.parse(ended)).getSeconds();
        } catch (Exception e) {
            return null;
        }
    }

    private static void sessionAlias(Map<String, Object> attributes, JsonNode source,
                                     String from, String to) {
        if (source.has(from)) {
            attributes.putIfAbsent(to, source.get(from));
        }
    }
}
