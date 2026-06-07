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
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.FhirGatewayServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
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
    private static final Set<String> UNSUPPORTED_ROUTING_TYPES = Set.of("ON_CALL", "POOL", "NATIONAL_POOL");
    private static final Set<String> TEAM_ROUTING_TYPES = Set.of("TEAM", "SPECIALTY_POOL");
    private static final Set<String> PRACTITIONER_ROUTING_TYPES = Set.of("PRACTITIONER", "PROVIDER");

    private final PctServiceClient pctClient;
    private final MvumoServiceClient mvumoClient;
    private final DocumentServiceClient documentClient;
    private final VarapiServiceClient varapiClient;
    private final TusoServiceClient tusoClient;
    private final NotificationServiceClient notificationClient;
    private final FhirGatewayServiceClient fhirGatewayClient;
    private final CostaServiceClient costaClient;
    private final AnalyticsPipelineServiceClient analyticsClient;
    private final TelemedicineGovernanceService telemedicineGovernanceService;
    private final ObjectMapper objectMapper;

    public TeleconsultController(PctServiceClient pctClient,
                                 MvumoServiceClient mvumoClient,
                                 DocumentServiceClient documentClient,
                                 VarapiServiceClient varapiClient,
                                 TusoServiceClient tusoClient,
                                 NotificationServiceClient notificationClient,
                                 FhirGatewayServiceClient fhirGatewayClient,
                                 CostaServiceClient costaClient,
                                 AnalyticsPipelineServiceClient analyticsClient,
                                 TelemedicineGovernanceService telemedicineGovernanceService,
                                 ObjectMapper objectMapper) {
        this.pctClient = pctClient;
        this.mvumoClient = mvumoClient;
        this.documentClient = documentClient;
        this.varapiClient = varapiClient;
        this.tusoClient = tusoClient;
        this.notificationClient = notificationClient;
        this.fhirGatewayClient = fhirGatewayClient;
        this.costaClient = costaClient;
        this.analyticsClient = analyticsClient;
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
            var created = pctClient.createReferral(payload);
            if (created == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No teleconsult session payload returned", requestId, correlationId);
            }
            emitTelemedicineNotification("TELECONSULT_REQUESTED", created, actorId, "A teleconsult request is waiting for specialist review.");
            telemedicineGovernanceService.audit(
                    tenantId, correlationId, normalizedPurpose, facilityId,
                    "TELEMEDICINE_SESSION_CREATED", "POST:teleconsult/sessions", "SUCCESS",
                    actorId, "PROVIDER", val(body, "patientId", "patient_id"), "TeleconsultSession",
                    extractId(created), Map.of("mode", "virtual"));
            return ok(created, requestId, correlationId, HttpStatus.CREATED);
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
            var completed = pctClient.completeReferral(id, body == null ? Map.of() : body);
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

    private record ValidationError(HttpStatus status, String code, String message) {
        ResponseEntity<Map<String, Object>> toResponse(String requestId, String correlationId) {
            return ResponseEntity.status(status).body(Map.of(
                    "error", Map.of("code", code, "message", message),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
