package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.core.telemedicine.SessionProvisioningResult;
import zw.gov.mohcc.impilo.pct.core.telemedicine.TelemedicineProviderProperties;
import zw.gov.mohcc.impilo.pct.core.telemedicine.TelemedicineSessionProvider;
import zw.gov.mohcc.impilo.pct.core.telemedicine.TelemedicineSessionProviderRouter;
import zw.gov.mohcc.impilo.pct.integration.LiveSessionIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.domain.ReferralStatus;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TelehealthSessionEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReferralRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.TelehealthSessionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TelemedicineOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(TelemedicineOrchestrationService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {};

    private final ReferralRepository referralRepository;
    private final TelehealthSessionRepository telehealthSessionRepository;
    private final EventOutboxRepository outboxRepository;
    private final TelemetryService telemetryService;
    private final TelemedicineSessionProviderRouter sessionProviderRouter;
    private final TelemedicineProviderProperties providerProperties;
    private final LiveSessionIntegration liveSessionIntegration;
    private final VirtualPoolQueueService virtualPoolQueueService;
    private final ReferralStateMachine referralStateMachine;
    private final ReferralTransitionRecorder gateRecorder;
    private final zw.gov.mohcc.impilo.pct.core.telemedicine.ReferralGateProperties gateProperties;
    private final ObjectMapper objectMapper;

    /** Consent postures that satisfy the submit gate (video/audio media referrals). */
    private static final java.util.Set<String> CONSENT_SATISFIED =
            java.util.Set.of("GRANTED", "OBTAINED", "WAIVED", "EMERGENCY_APPROVED", "NOT_REQUIRED");
    private static final java.util.Set<String> MEDIA_VIRTUAL_MODES =
            java.util.Set.of("VIDEO", "AUDIO");

    public TelemedicineOrchestrationService(
            ReferralRepository referralRepository,
            TelehealthSessionRepository telehealthSessionRepository,
            EventOutboxRepository outboxRepository,
            TelemetryService telemetryService,
            TelemedicineSessionProviderRouter sessionProviderRouter,
            TelemedicineProviderProperties providerProperties,
            LiveSessionIntegration liveSessionIntegration,
            VirtualPoolQueueService virtualPoolQueueService,
            ReferralStateMachine referralStateMachine,
            ReferralTransitionRecorder gateRecorder,
            zw.gov.mohcc.impilo.pct.core.telemedicine.ReferralGateProperties gateProperties,
            ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.telehealthSessionRepository = telehealthSessionRepository;
        this.outboxRepository = outboxRepository;
        this.telemetryService = telemetryService;
        this.sessionProviderRouter = sessionProviderRouter;
        this.providerProperties = providerProperties;
        this.liveSessionIntegration = liveSessionIntegration;
        this.virtualPoolQueueService = virtualPoolQueueService;
        this.referralStateMachine = referralStateMachine;
        this.gateRecorder = gateRecorder;
        this.gateProperties = gateProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Consent-on-submit gate (TM-B8). A video/audio media referral must carry a satisfied
     * consent posture before it is submitted. Shadow-then-enforce: records the decision and,
     * only in ENFORCE, rejects with {@code CONSENT_REQUIRED_MISSING} 409. Non-media referrals
     * (message/async, or no virtual mode) are exempt.
     */
    private void assertConsentForSubmit(ReferralEntity entity) {
        var mode = gateProperties.getMode();
        if (mode == zw.gov.mohcc.impilo.pct.core.telemedicine.ReferralGateProperties.Mode.OFF) {
            return;
        }
        String virtualMode = defaulted(entity.getVirtualMode(), "").trim().toUpperCase(Locale.ROOT);
        boolean mediaReferral = MEDIA_VIRTUAL_MODES.contains(virtualMode);
        if (!mediaReferral) {
            return; // consent-on-submit applies to governed media referrals only
        }
        String consentStatus = defaulted(entity.getConsentStatus(), "").trim().toUpperCase(Locale.ROOT);
        boolean satisfied = CONSENT_SATISFIED.contains(consentStatus);
        try {
            gateRecorder.recordGate("consent_submit", satisfied, mode.name(),
                    "virtualMode=" + virtualMode + " consentStatus=" + consentStatus);
        } catch (Exception e) {
            log.warn("Consent gate telemetry failed for referral {}: {}", entity.getReferralId(), e.getMessage());
        }
        if (!satisfied) {
            log.warn("Consent gate [{}] NOT satisfied for referral {} (virtualMode={}, consentStatus={})",
                    mode, entity.getReferralId(), virtualMode, consentStatus);
            if (mode == zw.gov.mohcc.impilo.pct.core.telemedicine.ReferralGateProperties.Mode.ENFORCE) {
                throw new PctDomainException("CONSENT_REQUIRED_MISSING", 409,
                        "A video/audio teleconsult cannot be submitted without captured consent.");
            }
        }
    }

    @Transactional
    public Map<String, Object> createReferral(Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        ReferralEntity referral = new ReferralEntity();
        referral.setTenantId(ctx.tenantId());
        referral.setPatientCpid(required(request, "patientId", "patient_id"));
        referral.setEncounterId(optional(request, "encounterId", "encounter_id"));
        referral.setProviderId(optional(request, "providerId", "provider_id", "referredBy", "referred_by"));
        referral.setFacilityId(optional(request, "facilityId", "facility_id", "referredToFacility", "referred_to_facility"));
        referral.setReferralType(defaulted(optional(request, "referralType", "referral_type"), "SPECIALIST"));
        referral.setSpecialty(optional(request, "specialty"));
        referral.setUrgency(defaulted(optional(request, "urgency", "priority"), "ROUTINE"));
        referral.setReason(required(request, "reason", "clinical_question"));
        referral.setClinicalSummary(optional(request, "clinicalSummary", "clinical_summary", "patient_summary"));
        referral.setPreferredMode(optional(request, "preferredMode", "preferred_mode"));
        referral.setModality(defaulted(optional(request, "modality"), "virtual"));
        referral.setVirtualMode(defaulted(optional(request, "virtual_mode", "virtualMode"), "video"));
        referral.setPatientCategory(optional(request, "patientCategory", "patient_category"));
        referral.setFacilityCategory(optional(request, "facilityCategory", "facility_category"));
        referral.setStatus("DRAFT");
        referral.setStage(1);
        referral.setRoutingTarget("{}");
        referral.setAttachmentDocumentIds("[]");
        referral.setMessages("[]");
        referral.setResponses("[]");
        referral.setCompletionPayload("{}");

        // Pool routing supplied at creation (citizen virtual-care lane): persist the V020
        // routing columns immediately so the referral lands in the pool worklist on submit
        // without requiring a separate /route call.
        String routingPoolId = optional(request, "routing_pool_id", "routingPoolId", "pool_id", "poolId");
        if (routingPoolId != null) {
            String routingKind = defaulted(optional(request, "routing_kind", "routingKind"), "POOL")
                    .trim().toUpperCase(Locale.ROOT);
            referral.setRoutingKind(routingKind);
            referral.setRoutingPoolId(routingPoolId);
            referral.setRoutedAt(OffsetDateTime.now());
            Map<String, Object> routingTarget = new LinkedHashMap<>();
            routingTarget.put("kind", routingKind);
            routingTarget.put("poolId", routingPoolId);
            referral.setRoutingTarget(writeJsonObject(routingTarget));
        }

        ReferralEntity saved = referralRepository.save(referral);
        emitOutbox("telemedicine.session.referral_created", saved.getReferralId().toString(), toReferralPayload(saved));
        telemetryService.record("telemedicine.referral.created", null, Map.of(
                "referralId", saved.getReferralId().toString(),
                "specialty", defaulted(saved.getSpecialty(), "GENERAL"),
                "urgency", saved.getUrgency()));
        return toReferralPayload(saved);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPatientReferrals(String patientId) {
        TrustContext ctx = TrustContextHolder.require();
        return referralRepository.findByTenantIdAndPatientCpidOrderByCreatedAtDesc(ctx.tenantId(), patientId)
                .stream()
                .map(this::toReferralPayload)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listIncomingReferrals(String facilityId, String status) {
        TrustContext ctx = TrustContextHolder.require();
        List<ReferralEntity> rows;
        if (status == null || status.isBlank()) {
            rows = referralRepository.findByTenantIdAndFacilityIdOrderByCreatedAtDesc(ctx.tenantId(), facilityId);
        } else {
            rows = referralRepository.findByTenantIdAndFacilityIdAndStatusOrderByCreatedAtDesc(
                    ctx.tenantId(), facilityId, status.trim().toUpperCase(Locale.ROOT));
        }
        return rows.stream().map(this::toReferralPayload).toList();
    }

    /**
     * Pool-scoped queue listing (Stage 3 routing): referrals routed to a specialty pool,
     * oldest first. Defaults to SUBMITTED (awaiting acceptance); {@code status} accepts a
     * comma-separated status-in list for wider worklists.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPoolReferrals(String poolId, String status) {
        TrustContext ctx = TrustContextHolder.require();
        List<String> statuses = status == null || status.isBlank()
                ? List.of("SUBMITTED")
                : java.util.Arrays.stream(status.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> s.toUpperCase(Locale.ROOT))
                        .toList();
        return referralRepository
                .findByTenantIdAndRoutingPoolIdAndStatusInOrderByCreatedAtAsc(ctx.tenantId(), poolId, statuses)
                .stream()
                .map(this::toReferralPayload)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getReferral(String referralId) {
        TrustContext ctx = TrustContextHolder.require();
        ReferralEntity entity = referralRepository.findByTenantIdAndReferralId(ctx.tenantId(), parseUuid(referralId, "referralId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Referral not found"));
        return toReferralPayload(entity);
    }

    @Transactional
    public Map<String, Object> updateReferralStage(String referralId, Map<String, Object> request) {
        ReferralEntity entity = getReferralEntity(referralId);
        entity.setStage(asInt(request.get("stage"), entity.getStage()));
        entity.setClinicalSummary(defaulted(optional(request, "patient_summary", "patientSummary"), entity.getClinicalSummary()));
        entity.setReason(defaulted(optional(request, "clinical_question", "clinicalQuestion"), entity.getReason()));
        entity.setPreferredMode(defaulted(optional(request, "preferredMode", "preferred_mode"), entity.getPreferredMode()));

        Object routingTarget = request.get("routing_target");
        if (routingTarget == null) routingTarget = request.get("routingTarget");
        if (routingTarget != null) entity.setRoutingTarget(writeJsonObject(routingTarget));

        Object attachments = request.get("attachment_document_ids");
        if (attachments == null) attachments = request.get("attachmentReferences");
        if (attachments != null) entity.setAttachmentDocumentIds(writeJsonArray(attachments));

        String status = optional(request, "status");
        if (status != null) referralStateMachine.applyRaw(entity, status, "stage-update");

        // G18: persist the booking↔referral back-link the BFF sends after scheduling a teleconsult
        // appointment (previously swallowed — pct_referrals had no column for it).
        String appointmentId = optional(request, "appointment_id", "appointmentId");
        if (appointmentId != null && !appointmentId.isBlank()) entity.setAppointmentId(appointmentId);
        String scheduledAt = optional(request, "scheduled_at", "scheduledAt");
        if (scheduledAt != null && !scheduledAt.isBlank()) {
            try {
                entity.setScheduledAt(OffsetDateTime.parse(scheduledAt));
            } catch (java.time.format.DateTimeParseException ignore) {
                // Non-ISO scheduled_at is ignored rather than failing the whole stage update.
            }
        }

        ReferralEntity saved = referralRepository.save(entity);
        emitOutbox("telemedicine.session.referral_updated", saved.getReferralId().toString(), toReferralPayload(saved));
        return toReferralPayload(saved);
    }

    @Transactional
    public Map<String, Object> updateReferralConsent(String referralId, Map<String, Object> request) {
        ReferralEntity entity = getReferralEntity(referralId);
        entity.setConsentType(optional(request, "consent_type", "consentType"));
        entity.setConsentStatus(defaulted(optional(request, "consent_status", "status"), "PENDING"));
        entity.setConsentReference(optional(request, "consent_reference", "consentReference"));
        entity.setMvumoSessionId(optional(request, "mvumo_session_id", "mvumoSessionId"));
        entity.setTshepoDecisionId(optional(request, "tshepo_decision_id", "tshepoDecisionId"));
        ReferralEntity saved = referralRepository.save(entity);
        emitOutbox("telemedicine.session.consent_updated", saved.getReferralId().toString(), toReferralPayload(saved));
        return toReferralPayload(saved);
    }

    @Transactional
    public Map<String, Object> submitReferral(String referralId) {
        ReferralEntity entity = getReferralEntity(referralId);
        assertConsentForSubmit(entity);
        referralStateMachine.apply(entity, ReferralStatus.SUBMITTED, "submit");
        entity.setSubmittedAt(OffsetDateTime.now());
        ReferralEntity saved = referralRepository.save(entity);
        // Pool-routed referrals enter the pool's materialised queue (priority from
        // urgency, SLA deadline from queue rules). No queue materialised → logged
        // honest degradation inside the service; the referral stays pool-listable.
        virtualPoolQueueService.enqueueForReferral(saved);
        emitOutbox("telemedicine.session.followup_required", saved.getReferralId().toString(), toReferralPayload(saved));
        return toReferralPayload(saved);
    }

    /**
     * Provider-authority-on-accept gate (TM-B8). The BFF resolves the accepting provider's
     * VARAPI status (derived axis: ACTIVE / SUSPENDED / REVOKED) and attaches a structured
     * {@code authority} block. PCT is the authoritative backstop: an unresolved or non-ACTIVE
     * provider fails the gate. Shadow-then-enforce — SHADOW records, ENFORCE rejects 403
     * {@code PROVIDER_NOT_AUTHORIZED}. Licence/active axes fail closed.
     */
    @SuppressWarnings("unchecked")
    private void assertAuthorityForAccept(ReferralEntity entity, Map<String, Object> request) {
        var mode = gateProperties.getMode();
        if (mode == zw.gov.mohcc.impilo.pct.core.telemedicine.ReferralGateProperties.Mode.OFF) {
            return;
        }
        Object authorityObj = request == null ? null : request.get("authority");
        Map<String, Object> authority = authorityObj instanceof Map ? (Map<String, Object>) authorityObj : Map.of();
        boolean resolved = Boolean.parseBoolean(String.valueOf(authority.getOrDefault("resolved", "false")));
        String status = defaulted(asString(authority.get("status")), "").trim().toUpperCase(Locale.ROOT);
        boolean satisfied = resolved && "ACTIVE".equals(status);
        try {
            gateRecorder.recordGate("authority_accept", satisfied, mode.name(),
                    "resolved=" + resolved + " status=" + status);
        } catch (Exception e) {
            log.warn("Authority gate telemetry failed for referral {}: {}", entity.getReferralId(), e.getMessage());
        }
        if (!satisfied) {
            log.warn("Authority gate [{}] NOT satisfied for referral {} (resolved={}, status={})",
                    mode, entity.getReferralId(), resolved, status);
            if (mode == zw.gov.mohcc.impilo.pct.core.telemedicine.ReferralGateProperties.Mode.ENFORCE) {
                throw new PctDomainException("PROVIDER_NOT_AUTHORIZED", 403,
                        "The accepting provider is not in good standing (status=" + status + ").");
            }
        }
    }

    @Transactional
    public Map<String, Object> acceptReferral(String referralId, Map<String, Object> request) {
        ReferralEntity entity = getReferralEntity(referralId);
        assertAuthorityForAccept(entity, request);
        referralStateMachine.apply(entity, ReferralStatus.ACCEPTED, "accept");
        Map<String, Object> acceptance = new LinkedHashMap<>();
        acceptance.put("responseType", "ACCEPTED");
        acceptance.put("acceptedBy", defaulted(optional(request, "accepted_by", "acceptedBy"), "unknown"));
        acceptance.put("timestamp", OffsetDateTime.now().toString());
        // Soft duty gate (never blocks): pool-routed acceptances record whether
        // the accepting provider pool had on-duty coverage at accept time. The
        // BFF supplies on_duty from vashandi; absent/unreachable = UNKNOWN.
        if ("POOL".equalsIgnoreCase(defaulted(entity.getRoutingKind(), ""))) {
            acceptance.put("onDuty", normalizeOnDuty(optional(request, "on_duty", "onDuty")));
        }
        appendResponse(entity, acceptance);
        ReferralEntity saved = referralRepository.save(entity);
        // Accepting a pool-routed referral completes (dequeues) its pool queue item.
        virtualPoolQueueService.completeForReferral(saved,
                defaulted(optional(request, "accepted_by", "acceptedBy"), "unknown"));
        emitOutbox("telemedicine.session.created", saved.getReferralId().toString(), toReferralPayload(saved));
        return toReferralPayload(saved);
    }

    /** Duty audit vocabulary: true | false | UNKNOWN (anything unparseable is UNKNOWN). */
    private static String normalizeOnDuty(String raw) {
        if (raw == null || raw.isBlank()) return "UNKNOWN";
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(v)) return "true";
        if ("false".equals(v)) return "false";
        return "UNKNOWN";
    }

    @Transactional
    public Map<String, Object> respondReferral(String referralId, Map<String, Object> request) {
        ReferralEntity entity = getReferralEntity(referralId);
        String responseType = defaulted(optional(request, "response_type", "responseType"), "MESSAGE")
                .trim().toUpperCase(Locale.ROOT);
        String message = defaulted(optional(request, "message", "response_notes", "notes"), "No message provided");
        String responderId = defaulted(optional(request, "responder_id", "responderId"), "unknown");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("responseType", responseType);
        response.put("message", message);
        response.put("responderId", responderId);
        response.put("timestamp", OffsetDateTime.now().toString());

        appendResponse(entity, response);
        if ("DECLINED".equals(responseType)) {
            referralStateMachine.apply(entity, ReferralStatus.DECLINED, "respond-decline");
        } else if ("MESSAGE".equals(responseType)) {
            appendMessage(entity, response);
            referralStateMachine.applyRaw(entity, defaulted(entity.getStatus(), "IN_REVIEW"), "respond-message");
        } else {
            referralStateMachine.apply(entity, ReferralStatus.RESPONDED, "respond");
        }

        ReferralEntity saved = referralRepository.save(entity);
        emitOutbox("telemedicine.session.support_requested", saved.getReferralId().toString(), toReferralPayload(saved));
        return toReferralPayload(saved);
    }

    // ------------------------------------------------------------------
    // Lifecycle actions (TM-B1): reason-bound transitions to the additive states.
    // Each records the reason on the responses log and drives the guard.
    // ------------------------------------------------------------------

    /** The current status and the guard-permitted next actions — powers contextual UI. */
    @Transactional(readOnly = true)
    public Map<String, Object> allowedActions(String referralId) {
        ReferralEntity entity = getReferralEntity(referralId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("referralId", entity.getReferralId().toString());
        out.put("status", entity.getStatus());
        List<String> targets = ReferralStatus.fromLenient(entity.getStatus())
                .map(s -> referralStateMachine.allowedTargets(s).stream().map(Enum::name).sorted().toList())
                .orElse(List.of());
        out.put("allowedTargets", targets);
        return out;
    }

    @Transactional
    public Map<String, Object> cancelReferral(String referralId, Map<String, Object> request) {
        return lifecycleTransition(referralId, ReferralStatus.CANCELLED, "cancel", request,
                "telemedicine.session.cancelled");
    }

    @Transactional
    public Map<String, Object> reopenReferral(String referralId, Map<String, Object> request) {
        return lifecycleTransition(referralId, ReferralStatus.REOPENED, "reopen", request,
                "telemedicine.session.reopened");
    }

    @Transactional
    public Map<String, Object> markEnteredInError(String referralId, Map<String, Object> request) {
        return lifecycleTransition(referralId, ReferralStatus.ENTERED_IN_ERROR, "error-mark", request,
                "telemedicine.session.entered_in_error");
    }

    @Transactional
    public Map<String, Object> escalateReferral(String referralId, Map<String, Object> request) {
        return lifecycleTransition(referralId, ReferralStatus.ESCALATED, "escalate", request,
                "telemedicine.session.escalated");
    }

    @Transactional
    public Map<String, Object> transferReferral(String referralId, Map<String, Object> request) {
        return lifecycleTransition(referralId, ReferralStatus.TRANSFERRED, "transfer", request,
                "telemedicine.session.transferred");
    }

    /** Shared lifecycle-transition mechanic: reason mandatory, guard-driven, reason logged. */
    private Map<String, Object> lifecycleTransition(String referralId, ReferralStatus target, String action,
                                                    Map<String, Object> request, String eventType) {
        ReferralEntity entity = getReferralEntity(referralId);
        String reason = optional(request, "reason");
        if (blank(reason)) {
            throw new PctDomainException("REASON_REQUIRED", 400, action + " requires a reason.");
        }
        referralStateMachine.apply(entity, target, action);
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("responseType", target.name());
        logEntry.put("action", action);
        logEntry.put("reason", reason);
        logEntry.put("actorId", TrustContextHolder.get() != null ? TrustContextHolder.get().actorId() : "system");
        logEntry.put("timestamp", OffsetDateTime.now().toString());
        appendResponse(entity, logEntry);
        ReferralEntity saved = referralRepository.save(entity);
        emitOutbox(eventType, saved.getReferralId().toString(), toReferralPayload(saved));
        return toReferralPayload(saved);
    }

    /**
     * Stage 6 (Response Package): record a STRUCTURED clinical response (C7) — diagnosis, action plan, red
     * flags, follow-up — instead of free-text only. The structured fields are persisted to
     * {@code structured_response} and a summary line is also appended to the responses log for the timeline.
     */
    @Transactional
    public Map<String, Object> respondReferralStructured(String referralId, Map<String, Object> request) {
        ReferralEntity entity = getReferralEntity(referralId);
        String responderId = defaulted(optional(request, "responder_id", "responderId"), "unknown");

        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("diagnosis", optional(request, "diagnosis"));
        structured.put("actionPlan", optional(request, "action_plan", "actionPlan"));
        structured.put("redFlags", optional(request, "red_flags", "redFlags"));
        structured.put("followUp", optional(request, "follow_up", "followUp"));
        structured.put("responderId", responderId);
        structured.put("timestamp", OffsetDateTime.now().toString());
        entity.setStructuredResponse(writeJsonObject(structured));

        appendResponse(entity, Map.of(
                "responseType", "STRUCTURED",
                "responderId", responderId,
                "diagnosis", defaulted(asString(structured.get("diagnosis")), ""),
                "timestamp", OffsetDateTime.now().toString()));
        referralStateMachine.apply(entity, ReferralStatus.RESPONDED, "respond-structured");
        if (entity.getStage() != null && entity.getStage() < 6) {
            entity.setStage(6);
        }
        ReferralEntity saved = referralRepository.save(entity);
        emitOutbox("telemedicine.session.response_submitted", saved.getReferralId().toString(), toReferralPayload(saved));
        return toReferralPayload(saved);
    }

    /**
     * Stage 3 (Routing & Worklists): route a referral to a team/pool/on-call roster (not only a single named
     * provider). Records the routing kind + pool id and timestamps the routing.
     */
    @Transactional
    public Map<String, Object> routeReferral(String referralId, Map<String, Object> request) {
        ReferralEntity entity = getReferralEntity(referralId);
        String kind = defaulted(optional(request, "routing_kind", "routingKind"), "POOL")
                .trim().toUpperCase(Locale.ROOT);
        entity.setRoutingKind(kind);
        entity.setRoutingPoolId(optional(request, "routing_pool_id", "routingPoolId", "pool_id", "poolId"));
        entity.setRoutedAt(OffsetDateTime.now());

        Map<String, Object> routingTarget = new LinkedHashMap<>();
        routingTarget.put("kind", kind);
        routingTarget.put("poolId", entity.getRoutingPoolId());
        routingTarget.put("onCallRosterId", optional(request, "on_call_roster_id", "onCallRosterId"));
        entity.setRoutingTarget(writeJsonObject(routingTarget));
        if (entity.getStage() != null && entity.getStage() < 3) {
            entity.setStage(3);
        }
        if ("DRAFT".equals(entity.getStatus())) {
            referralStateMachine.apply(entity, ReferralStatus.ROUTED, "route");
        }
        ReferralEntity saved = referralRepository.save(entity);
        emitOutbox("telemedicine.session.routed", saved.getReferralId().toString(), toReferralPayload(saved));
        return toReferralPayload(saved);
    }

    @Transactional
    public Map<String, Object> completeReferral(String referralId, Map<String, Object> request) {
        ReferralEntity entity = getReferralEntity(referralId);
        // Idempotency: completion emits a BILLABLE value event (TELECONSULT_COMPLETED -> CHARGE_CREATED in
        // COSTA). A redelivered/double-clicked completion must NOT re-emit it, or the patient is charged twice.
        // Return the current payload unchanged without re-running the completion side effects.
        if ("COMPLETED".equals(entity.getStatus())) {
            return toReferralPayload(entity);
        }
        // Structured referrer Completion Note (G-CT-02 / Addendum-2: "no closure without audit").
        // A telemedicine referral cannot be closed with an empty note — closure must carry audit meaning.
        String actionsTaken = optional(request, "actionsTaken", "actions_taken");
        String patientOutcome = optional(request, "patientOutcome", "patient_outcome");
        String closureNarrative = optional(request, "closureNarrative", "closure_narrative");
        String followUp = optional(request, "followUp", "follow_up");
        if (blank(actionsTaken) && blank(patientOutcome) && blank(closureNarrative)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A telemedicine referral cannot be closed without a completion note "
                            + "(actions taken, patient outcome, or closure narrative).");
        }

        referralStateMachine.apply(entity, ReferralStatus.COMPLETED, "complete");
        entity.setCompletedAt(OffsetDateTime.now());
        // Billing context may be finalised at completion (e.g. eligibility resolved); keep what
        // was captured at referral time unless the completion request overrides it.
        entity.setPatientCategory(defaulted(optional(request, "patientCategory", "patient_category"),
                entity.getPatientCategory()));
        entity.setFacilityCategory(defaulted(optional(request, "facilityCategory", "facility_category"),
                entity.getFacilityCategory()));

        // Persist the structured, auditable completion note alongside any extra request fields.
        Map<String, Object> completion = new LinkedHashMap<>(request == null ? Map.of() : request);
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("actionsTaken", actionsTaken);
        note.put("patientOutcome", patientOutcome);
        note.put("closureNarrative", closureNarrative);
        note.put("followUp", followUp);
        note.put("completedBy", TrustContextHolder.require().actorId());
        note.put("completedAt", entity.getCompletedAt().toString());
        completion.put("completionNote", note);
        entity.setCompletionPayload(writeJsonObject(completion));
        ReferralEntity saved = referralRepository.save(entity);
        emitOutbox("telemedicine.session.completed", saved.getReferralId().toString(), toReferralPayload(saved));
        emitTeleconsultValueTrigger(saved);
        telemetryService.record("telemedicine.referral.completed", null, Map.of(
                "referralId", saved.getReferralId().toString(),
                "patientCpid", saved.getPatientCpid(),
                "specialty", defaulted(saved.getSpecialty(), "GENERAL")));
        return toReferralPayload(saved);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPatientTelehealthSessions(String patientCpid, String status) {
        TrustContext ctx = TrustContextHolder.require();
        List<TelehealthSessionEntity> rows = status == null || status.isBlank()
                ? telehealthSessionRepository.findByTenantIdAndPatientCpidOrderByCreatedAtDesc(ctx.tenantId(), patientCpid)
                : telehealthSessionRepository.findByTenantIdAndPatientCpidAndStatusOrderByCreatedAtDesc(
                ctx.tenantId(), patientCpid, status.trim().toUpperCase(Locale.ROOT));
        return rows.stream().map(this::toTelehealthPayload).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listFacilityTelehealthSessions(String facilityId, String status) {
        TrustContext ctx = TrustContextHolder.require();
        List<TelehealthSessionEntity> rows = status == null || status.isBlank()
                ? telehealthSessionRepository.findByTenantIdAndFacilityIdOrderByCreatedAtDesc(ctx.tenantId(), facilityId)
                : telehealthSessionRepository.findByTenantIdAndFacilityIdAndStatusOrderByCreatedAtDesc(
                ctx.tenantId(), facilityId, status.trim().toUpperCase(Locale.ROOT));
        return rows.stream().map(this::toTelehealthPayload).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTelehealthSession(String sessionId) {
        TrustContext ctx = TrustContextHolder.require();
        TelehealthSessionEntity entity = telehealthSessionRepository
                .findByTenantIdAndSessionId(ctx.tenantId(), parseUuid(sessionId, "sessionId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Telehealth session not found"));
        return toTelehealthPayload(entity);
    }

    @Transactional
    public Map<String, Object> createTelehealthSession(Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        TelehealthSessionEntity entity = new TelehealthSessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setTenantId(ctx.tenantId());
        entity.setPatientCpid(required(request, "patientId", "patient_id", "patientCpid"));
        entity.setProviderId(optional(request, "providerId", "provider_id"));
        entity.setFacilityId(optional(request, "facilityId", "facility_id"));
        entity.setEncounterId(optional(request, "encounterId", "encounter_id"));
        String referralId = optional(request, "referralId", "referral_id");
        if (referralId != null && !referralId.isBlank()) {
            entity.setReferralId(parseUuid(referralId, "referralId"));
        }
        entity.setSessionType(defaulted(optional(request, "sessionType", "session_type"), "VIDEO").toUpperCase(Locale.ROOT));
        entity.setStatus("SCHEDULED");
        entity.setScheduledAt(parseDate(optional(request, "scheduledAt", "scheduled_at"), OffsetDateTime.now().plusMinutes(30)));
        String requestedProvider = optional(request, "sessionProvider", "session_provider", "providerType", "provider_type");
        String normalizedPurpose = defaulted(optional(request, "purposeOfUse", "purpose_of_use"), "TREATMENT").toUpperCase(Locale.ROOT);
        String consentReference = optional(request, "consentReference", "consent_reference");
        assertMediaConsentReference(entity.getSessionType(), consentReference, normalizedPurpose);
        Map<String, Object> provisioningAttributes = new LinkedHashMap<>(request == null ? Map.of() : request);
        provisioningAttributes.put("sessionId", entity.getSessionId().toString());
        provisioningAttributes.put("purposeOfUse", normalizedPurpose);
        provisioningAttributes.put("consentReference", consentReference);

        if (shouldUseImpiloLive(request, entity)) {
            String clinicalContext = entity.getEncounterId() != null && !entity.getEncounterId().isBlank()
                    ? entity.getEncounterId()
                    : entity.getSessionId().toString();
            Map<String, Object> livePayload = liveSessionIntegration.buildClinicalPayload(
                    clinicalContext,
                    entity.getPatientCpid(),
                    entity.getProviderId(),
                    entity.getFacilityId(),
                    optional(request, "title"),
                    optional(request, "description", "notes"),
                    entity.getScheduledAt(),
                    optional(request, "endTime", "end_time"),
                    consentReference,
                    optional(request, "recordingPolicy", "recording_policy"),
                    Boolean.TRUE.equals(request != null ? request.get("emergencyFlag") : null));
            liveSessionIntegration.scheduleClinicalSession(livePayload).ifPresent(liveEvent -> {
                String liveEventId = asString(liveEvent.get("id"));
                if (liveEventId != null && !liveEventId.isBlank()) {
                    entity.setLiveEventId(parseUuid(liveEventId, "liveEventId"));
                    entity.setRoomUrl("/live/event/" + liveEventId + "/room");
                    entity.setChannel("IMPILO_LIVE");
                }
            });
        }

        if (entity.getChannel() == null || entity.getChannel().isBlank()) {
            SessionProvisioningResult provisioning = sessionProviderRouter.provision(
                    requestedProvider,
                    new TelemedicineSessionProvider.SessionProvisioningRequest(
                            ctx.tenantId(),
                            entity.getPatientCpid(),
                            entity.getProviderId(),
                            entity.getFacilityId(),
                            entity.getSessionType(),
                            referralId,
                            entity.getEncounterId(),
                            provisioningAttributes));
            entity.setRoomUrl(defaulted(optional(request, "roomUrl", "room_url"), provisioning.roomUrl()));
            entity.setChannel(defaulted(optional(request, "channel"), provisioning.channel()));
            entity.setAccessToken(defaulted(optional(request, "accessToken", "access_token"), provisioning.accessToken()));
        }
        entity.setNotes(optional(request, "notes"));

        TelehealthSessionEntity saved = telehealthSessionRepository.save(entity);
        emitOutbox("telemedicine.session.created", saved.getSessionId().toString(), toTelehealthPayload(saved));
        emitOutbox("telemedicine.session.scheduled", saved.getSessionId().toString(), toTelehealthPayload(saved));
        emitOutbox("telemedicine.session.join_link.created", saved.getSessionId().toString(), toTelehealthPayload(saved));
        emitOutbox("telemedicine.session.room_provisioned", saved.getSessionId().toString(), toTelehealthPayload(saved));
        telemetryService.record("telemedicine.session.created", null, Map.of(
                "sessionId", saved.getSessionId().toString(),
                "sessionType", saved.getSessionType(),
                "facilityId", defaulted(saved.getFacilityId(), "UNKNOWN")));

        if (saved.getReferralId() != null) {
            referralRepository.findByTenantIdAndReferralId(ctx.tenantId(), saved.getReferralId()).ifPresent(referral -> {
                referralStateMachine.apply(referral, ReferralStatus.SCHEDULED, "schedule-backlink");
                referralRepository.save(referral);
            });
        }
        return toTelehealthPayload(saved);
    }

    @Transactional
    public Map<String, Object> joinTelehealthSession(String sessionId) {
        TelehealthSessionEntity entity = getTelehealthEntity(sessionId);
        entity.setStatus("IN_PROGRESS");
        if (entity.getStartedAt() == null) {
            entity.setStartedAt(OffsetDateTime.now());
        }
        TelehealthSessionEntity saved = telehealthSessionRepository.save(entity);
        emitOutbox("telemedicine.session.waiting_room.entered", saved.getSessionId().toString(), toTelehealthPayload(saved));
        emitOutbox("telemedicine.session.started", saved.getSessionId().toString(), toTelehealthPayload(saved));
        emitOutbox("telemedicine.session.media_started", saved.getSessionId().toString(), toTelehealthPayload(saved));
        return toTelehealthPayload(saved);
    }

    @Transactional
    public Map<String, Object> endTelehealthSession(String sessionId, Map<String, Object> request) {
        TelehealthSessionEntity entity = getTelehealthEntity(sessionId);
        OffsetDateTime now = OffsetDateTime.now();
        entity.setStatus("COMPLETED");
        if (entity.getStartedAt() == null) {
            entity.setStartedAt(now);
        }
        entity.setEndedAt(now);
        entity.setDurationSeconds(Duration.between(entity.getStartedAt(), now).getSeconds());
        if (request != null) {
            entity.setNotes(defaulted(optional(request, "notes"), entity.getNotes()));
        }
        TelehealthSessionEntity saved = telehealthSessionRepository.save(entity);
        sessionProviderRouter.end(resolveSessionProvider(saved.getChannel()), saved.getSessionId().toString());
        emitOutbox("telemedicine.session.completed", saved.getSessionId().toString(), toTelehealthPayload(saved));
        emitOutbox("telemedicine.session.media_ended", saved.getSessionId().toString(), toTelehealthPayload(saved));
        telemetryService.record("telemedicine.session.completed", null, Map.of(
                "sessionId", saved.getSessionId().toString(),
                "durationSeconds", saved.getDurationSeconds() == null ? 0L : saved.getDurationSeconds(),
                "facilityId", defaulted(saved.getFacilityId(), "UNKNOWN")));
        return toTelehealthPayload(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> telemedicineOpsSnapshot(String facilityId) {
        List<Map<String, Object>> openReferrals = listIncomingReferrals(facilityId, "SUBMITTED");
        List<Map<String, Object>> inProgressSessions = listFacilityTelehealthSessions(facilityId, "IN_PROGRESS");
        List<Map<String, Object>> scheduledSessions = listFacilityTelehealthSessions(facilityId, "SCHEDULED");

        long overdueScheduled = scheduledSessions.stream()
                .filter(row -> {
                    String raw = asString(row.get("scheduledAt"));
                    if (raw == null || raw.isBlank()) return false;
                    try {
                        return OffsetDateTime.parse(raw).isBefore(OffsetDateTime.now().minusMinutes(15));
                    } catch (Exception ignored) {
                        return false;
                    }
                })
                .count();

        return Map.of(
                "facilityId", facilityId,
                "submittedReferralBacklog", openReferrals.size(),
                "inProgressSessions", inProgressSessions.size(),
                "scheduledSessions", scheduledSessions.size(),
                "overdueScheduledSessions", overdueScheduled
        );
    }

    private ReferralEntity getReferralEntity(String referralId) {
        TrustContext ctx = TrustContextHolder.require();
        return referralRepository.findByTenantIdAndReferralId(ctx.tenantId(), parseUuid(referralId, "referralId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Referral not found"));
    }

    private TelehealthSessionEntity getTelehealthEntity(String sessionId) {
        TrustContext ctx = TrustContextHolder.require();
        return telehealthSessionRepository.findByTenantIdAndSessionId(ctx.tenantId(), parseUuid(sessionId, "sessionId"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Telehealth session not found"));
    }

    private Map<String, Object> toReferralPayload(ReferralEntity entity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", entity.getReferralId().toString());
        body.put("patientCpid", entity.getPatientCpid());
        body.put("encounterId", entity.getEncounterId());
        body.put("providerId", entity.getProviderId());
        body.put("facilityId", entity.getFacilityId());
        body.put("referralType", entity.getReferralType());
        body.put("specialty", entity.getSpecialty());
        body.put("urgency", entity.getUrgency());
        body.put("reason", entity.getReason());
        body.put("clinicalSummary", entity.getClinicalSummary());
        body.put("status", entity.getStatus());
        body.put("stage", entity.getStage());
        body.put("preferredMode", entity.getPreferredMode());
        body.put("modality", entity.getModality());
        body.put("virtualMode", entity.getVirtualMode());
        body.put("patientCategory", entity.getPatientCategory());
        body.put("facilityCategory", entity.getFacilityCategory());
        body.put("routingTarget", readJsonMap(entity.getRoutingTarget()));
        body.put("routingKind", entity.getRoutingKind());
        body.put("routingPoolId", entity.getRoutingPoolId());
        body.put("routedAt", toIso(entity.getRoutedAt()));
        body.put("appointmentId", entity.getAppointmentId());
        body.put("scheduledAt", toIso(entity.getScheduledAt()));
        body.put("recordingRef", entity.getRecordingRef());
        body.put("recordingStorageKey", entity.getRecordingStorageKey());
        body.put("structuredResponse", entity.getStructuredResponse() == null ? null : readJsonMap(entity.getStructuredResponse()));
        body.put("attachmentDocumentIds", readJsonStringList(entity.getAttachmentDocumentIds()));
        body.put("messages", readJsonMapList(entity.getMessages()));
        body.put("responses", readJsonMapList(entity.getResponses()));
        body.put("consentType", entity.getConsentType());
        body.put("consentStatus", entity.getConsentStatus());
        body.put("consentReference", entity.getConsentReference());
        body.put("mvumoSessionId", entity.getMvumoSessionId());
        body.put("tshepoDecisionId", entity.getTshepoDecisionId());
        body.put("completion", readJsonMap(entity.getCompletionPayload()));
        body.put("submittedAt", toIso(entity.getSubmittedAt()));
        body.put("completedAt", toIso(entity.getCompletedAt()));
        body.put("createdAt", toIso(entity.getCreatedAt()));
        body.put("updatedAt", toIso(entity.getUpdatedAt()));
        return body;
    }

    private Map<String, Object> toTelehealthPayload(TelehealthSessionEntity entity) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", entity.getSessionId().toString());
        body.put("patientCpid", entity.getPatientCpid());
        body.put("providerId", entity.getProviderId());
        body.put("facilityId", entity.getFacilityId());
        body.put("referralId", entity.getReferralId() == null ? null : entity.getReferralId().toString());
        body.put("encounterId", entity.getEncounterId());
        body.put("sessionType", entity.getSessionType());
        body.put("status", entity.getStatus());
        body.put("roomUrl", entity.getRoomUrl());
        body.put("channel", entity.getChannel());
        body.put("sessionProvider", resolveSessionProvider(entity.getChannel()));
        body.put("token", entity.getAccessToken());
        body.put("scheduledAt", toIso(entity.getScheduledAt()));
        body.put("startedAt", toIso(entity.getStartedAt()));
        body.put("endedAt", toIso(entity.getEndedAt()));
        body.put("durationSeconds", entity.getDurationSeconds());
        body.put("notes", entity.getNotes());
        body.put("liveEventId", entity.getLiveEventId() == null ? null : entity.getLiveEventId().toString());
        body.put("impiloLiveJoinPath", entity.getLiveEventId() == null
                ? null
                : "/live/event/" + entity.getLiveEventId() + "/room");
        body.put("createdAt", toIso(entity.getCreatedAt()));
        body.put("updatedAt", toIso(entity.getUpdatedAt()));
        return body;
    }

    /** Route VIDEO telehealth sessions through Impilo Live unless an alternate provider is explicit. */
    private boolean shouldUseImpiloLive(Map<String, Object> request, TelehealthSessionEntity entity) {
        if (!liveSessionIntegration.isEnabled()) {
            return false;
        }
        String sessionType = entity.getSessionType();
        if (sessionType == null || (!sessionType.contains("VIDEO") && !"AUDIO".equals(sessionType))) {
            return false;
        }
        String provider = optional(request, "sessionProvider", "session_provider", "providerType", "provider_type");
        if (provider != null && !provider.isBlank()) {
            return "impilo-live".equalsIgnoreCase(provider) || "IMPILO_LIVE".equalsIgnoreCase(provider);
        }
        if (request != null) {
            Object flag = request.get("useImpiloLive");
            if (flag == null) {
                flag = request.get("use_impilo_live");
            }
            if (Boolean.TRUE.equals(flag)) {
                return true;
            }
        }
        return true;
    }

    private void appendMessage(ReferralEntity entity, Map<String, Object> payload) {
        List<Map<String, Object>> messages = new ArrayList<>(readJsonMapList(entity.getMessages()));
        messages.add(payload);
        entity.setMessages(writeJsonArray(messages));
    }

    private void appendResponse(ReferralEntity entity, Map<String, Object> payload) {
        List<Map<String, Object>> responses = new ArrayList<>(readJsonMapList(entity.getResponses()));
        responses.add(payload);
        entity.setResponses(writeJsonArray(responses));
    }

    /**
     * Emit a flat-payload value-trigger event when a teleconsult referral completes so the
     * costing plane (COSTA / L4) can price it. Unlike {@link #emitOutbox}, the payload fields
     * are written at the top level (matching the encounter/order events COSTA already consumes)
     * rather than nested under an envelope. {@link zw.gov.mohcc.impilo.pct.events.OutboxPublisher}
     * routes the {@code TELECONSULT_COMPLETED} event type to {@code clinical.teleconsult.value}
     * and {@code core.transaction.events}.
     */
    private void emitTeleconsultValueTrigger(ReferralEntity referral) {
        TrustContext ctx = TrustContextHolder.require();
        String referralId = referral.getReferralId().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", referralId + ":TELECONSULT_COMPLETED");
        payload.put("tenantId", ctx.tenantId().toString());
        payload.put("referralId", referralId);
        payload.put("patientCpid", referral.getPatientCpid());
        payload.put("encounterId", referral.getEncounterId());
        payload.put("facilityId", referral.getFacilityId());
        payload.put("specialty", referral.getSpecialty());
        payload.put("modality", referral.getModality());
        payload.put("patientCategory", referral.getPatientCategory());
        payload.put("facilityCategory", referral.getFacilityCategory());
        payload.put("sourceServiceEvent", "TELECONSULT_COMPLETED");

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("telemedicine");
        outbox.setAggregateId(referralId);
        outbox.setEventType("TELECONSULT_COMPLETED");
        outbox.setTenantId(ctx.tenantId());
        outbox.setPayload(writeJsonObject(payload));
        outboxRepository.save(outbox);
    }

    /**
     * Attach an rtc-gateway recording to its teleconsult referral (G20). Called from the Kafka
     * consumer of {@code impilo.rtc.recording.available.v1} (no TrustContext) — matched via
     * {@code owningRef} = referralId. Idempotent; ignores unknown/non-teleconsult refs.
     */
    @Transactional
    public boolean attachRecording(String referralId, String recordingRef, String storageKey) {
        if (referralId == null || referralId.isBlank()) return false;
        UUID id;
        try {
            id = UUID.fromString(referralId);
        } catch (IllegalArgumentException e) {
            return false;
        }
        ReferralEntity entity = referralRepository.findByReferralId(id).orElse(null);
        if (entity == null) {
            log.warn("Recording available for unknown referral {} — ignoring", referralId);
            return false;
        }
        if (recordingRef != null && recordingRef.equals(entity.getRecordingRef())) {
            return true; // idempotent replay
        }
        entity.setRecordingRef(recordingRef);
        if (storageKey != null && !storageKey.isBlank()) entity.setRecordingStorageKey(storageKey);
        ReferralEntity saved = referralRepository.save(entity);
        emitOutbox("telemedicine.session.recording_attached", saved.getReferralId().toString(),
                saved.getTenantId(), toReferralPayload(saved));
        log.info("Attached recording {} to teleconsult referral {}", recordingRef, referralId);
        return true;
    }

    /** Outbox emit for consumer/system threads that carry no TrustContext (tenantId supplied). */
    private void emitOutbox(String eventType, String aggregateId, UUID tenantId, Map<String, Object> payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("telemedicine");
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setTenantId(tenantId);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventType", eventType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("tenantId", tenantId);
        envelope.put("occurredAt", OffsetDateTime.now().toString());
        envelope.put("payload", payload == null ? Map.of() : payload);
        outbox.setPayload(writeJsonObject(envelope));
        outboxRepository.save(outbox);
    }

    private void emitOutbox(String eventType, String aggregateId, Map<String, Object> payload) {
        TrustContext ctx = TrustContextHolder.require();
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("telemedicine");
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setTenantId(ctx.tenantId());
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventType", eventType);
        envelope.put("aggregateId", aggregateId);
        envelope.put("tenantId", ctx.tenantId());
        envelope.put("occurredAt", OffsetDateTime.now().toString());
        envelope.put("payload", payload == null ? Map.of() : payload);
        outbox.setPayload(writeJsonObject(envelope));
        outboxRepository.save(outbox);
    }

    private UUID parseUuid(String raw, String field) {
        try {
            return UUID.fromString(raw);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be a UUID");
        }
    }

    private String required(Map<String, Object> request, String... keys) {
        String value = optional(request, keys);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, keys[0] + " is required");
        }
        return value;
    }

    private String optional(Map<String, Object> request, String... keys) {
        if (request == null) return null;
        for (String key : keys) {
            Object value = request.get(key);
            if (value != null) {
                String as = value.toString().trim();
                if (!as.isBlank()) return as;
            }
        }
        return null;
    }

    private String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void assertMediaConsentReference(String sessionType, String consentReference, String purposeOfUse) {
        if (!providerProperties.getGovernance().isRequireConsentReferenceForMedia()) {
            return;
        }
        if (!"VIDEO".equalsIgnoreCase(sessionType) && !"AUDIO".equalsIgnoreCase(sessionType)) {
            return;
        }
        if (providerProperties.getGovernance().isAllowEmergencyWithoutConsent()
                && "EMERGENCY".equalsIgnoreCase(defaulted(purposeOfUse, "TREATMENT"))) {
            return;
        }
        if (consentReference == null || consentReference.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "consentReference is required for governed telemedicine media sessions");
        }
    }

    private int asInt(Object raw, int fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(raw.toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private OffsetDateTime parseDate(String raw, OffsetDateTime fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String writeJsonObject(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String writeJsonArray(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private Map<String, Object> readJsonMap(String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private List<String> readJsonStringList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return objectMapper.readValue(raw, STRING_LIST_TYPE);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> readJsonMapList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            return objectMapper.readValue(raw, MAP_LIST_TYPE);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String toIso(OffsetDateTime dt) {
        return dt == null ? null : dt.toString();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private String resolveSessionProvider(String channel) {
        if (channel == null || channel.isBlank()) {
            return TelemedicineSessionProviderRouter.DEFAULT_PROVIDER_TYPE;
        }
        if (channel.trim().toLowerCase(Locale.ROOT).startsWith("rtc:")) {
            return "EXTERNAL_MANAGED";
        }
        return switch (channel.trim().toLowerCase(Locale.ROOT)) {
            case "manual-phone" -> "MANUAL_PHONE";
            case "async-no-video" -> "ASYNC_NO_VIDEO";
            case "external-managed" -> "EXTERNAL_MANAGED";
            default -> TelemedicineSessionProviderRouter.DEFAULT_PROVIDER_TYPE;
        };
    }
}
