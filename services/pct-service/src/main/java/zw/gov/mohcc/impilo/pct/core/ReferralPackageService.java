package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralPackageEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ReferralPackageRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ReferralPackageService {
    private static final Set<String> MODALITIES = Set.of("in_person", "virtual", "hybrid");
    private static final Set<String> VIRTUAL_MODES = Set.of("async", "chat", "audio", "video", "scheduled", "board");
    private static final Set<String> CONSENT_TYPES = Set.of("digital", "verbal", "proxy", "emergency");
    private static final Set<String> CONSENT_STATUSES = Set.of("not_required", "pending", "obtained", "waived", "denied", "emergency_approved");
    private static final Set<String> ACTIVE_STATUSES = Set.of("DRAFT", "SUBMITTED", "ACCEPTED", "RESPONDED");
    private static final Set<String> ROUTING_TYPES = Set.of(
            "PRACTITIONER", "WORKSPACE", "UNIT", "FACILITY_SERVICE", "ON_CALL", "TEAM", "SPECIALTY_POOL", "NATIONAL_POOL");

    private final ReferralPackageRepository referralRepository;
    private final zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository encounterRepository;
    private final ObjectMapper objectMapper;

    public ReferralPackageService(ReferralPackageRepository referralRepository,
                                  zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository encounterRepository,
                                  ObjectMapper objectMapper) {
        this.referralRepository = referralRepository;
        this.encounterRepository = encounterRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReferralPackageEntity createDraft(Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        Long encounterId = requiredLong(request, "encounter_id", "encounterId");
        EncounterEntity encounter = encounterRepository.findByTenantIdAndId(ctx.tenantId(), encounterId)
                .orElseThrow(() -> new PctDomainException("MISSING_ENCOUNTER", 404, "Encounter not found: " + encounterId));

        String journeyId = str(request, "journey_id", "journeyId");
        if (journeyId != null && !journeyId.equals(encounter.getJourneyId())) {
            throw new PctDomainException("ENCOUNTER_JOURNEY_MISMATCH", 409,
                    "encounter_id and journey_id refer to different journeys");
        }

        ReferralPackageEntity entity = new ReferralPackageEntity();
        entity.setReferralId(UUID.randomUUID());
        entity.setTenantId(ctx.tenantId());
        entity.setEncounterId(encounterId);
        entity.setJourneyId(encounter.getJourneyId());
        entity.setPatientCpid(defaulted(str(request, "patient_id", "patientId"), encounter.getSubjectCpid()));
        entity.setProviderId(defaulted(str(request, "provider_id", "providerId"), ctx.actorId()));
        entity.setFacilityId(ctx.facilityId());
        entity.setWorkspaceId(ctx.workspaceId());
        entity.setUrgency(defaulted(str(request, "urgency"), "ROUTINE"));
        entity.setSpecialty(str(request, "specialty"));
        entity.setClinicalQuestion(str(request, "clinical_question", "clinicalQuestion", "reason"));
        entity.setReferralLetter(str(request, "referral_letter", "referralLetter"));
        entity.setPatientSummary(str(request, "patient_summary", "patientSummary"));
        entity.setVisitSummary(str(request, "visit_summary", "visitSummary"));
        entity.setModality(normalizeModality(str(request, "modality")));
        entity.setVirtualMode(normalizeVirtualMode(str(request, "virtual_mode", "virtualMode"), entity.getModality()));
        entity.setRoutingTarget(normalizeRoutingTarget(request.get("routing_target"), "{}"));
        entity.setAttachmentDocumentIds(normalizeAttachmentDocumentIds(request.get("attachment_document_ids"), "[]"));
        boolean consentRequired = bool(request.get("consent_required"), false);
        entity.setConsentRequired(consentRequired);
        entity.setConsentStatus(consentRequired ? "PENDING" : "NOT_REQUIRED");
        entity.setReferralPackageStatus("DRAFT");
        entity.setWorkflowStage(1);
        return referralRepository.save(entity);
    }

    @Transactional
    public ReferralPackageEntity updateStage(UUID referralId, int stage, Map<String, Object> request) {
        if (stage < 1 || stage > 7) {
            throw new PctDomainException("INVALID_STAGE", 400, "stage must be between 1 and 7");
        }
        ReferralPackageEntity entity = getOwned(referralId);
        ensureMutable(entity);

        if (request.containsKey("referral_letter") || request.containsKey("referralLetter")) {
            entity.setReferralLetter(str(request, "referral_letter", "referralLetter"));
        }
        if (request.containsKey("patient_summary") || request.containsKey("patientSummary")) {
            entity.setPatientSummary(str(request, "patient_summary", "patientSummary"));
        }
        if (request.containsKey("visit_summary") || request.containsKey("visitSummary")) {
            entity.setVisitSummary(str(request, "visit_summary", "visitSummary"));
        }
        if (request.containsKey("attachment_document_ids") || request.containsKey("attachments")) {
            Object source = request.containsKey("attachment_document_ids")
                    ? request.get("attachment_document_ids")
                    : request.get("attachments");
            entity.setAttachmentDocumentIds(normalizeAttachmentDocumentIds(source, entity.getAttachmentDocumentIds()));
        }
        if (request.containsKey("routing_target") || request.containsKey("routingType") || request.containsKey("routingTarget")) {
            Object value = request.containsKey("routing_target")
                    ? request.get("routing_target")
                    : Map.of(
                    "type", str(request, "routingType"),
                    "target", str(request, "routingTarget"));
            entity.setRoutingTarget(normalizeRoutingTarget(value, entity.getRoutingTarget()));
        }
        if (request.containsKey("virtual_mode") || request.containsKey("preferredMode")) {
            entity.setVirtualMode(normalizeVirtualMode(
                    str(request, "virtual_mode", "virtualMode", "preferredMode"),
                    entity.getModality()));
        }
        if (request.containsKey("modality")) {
            entity.setModality(normalizeModality(str(request, "modality")));
            entity.setVirtualMode(normalizeVirtualMode(entity.getVirtualMode(), entity.getModality()));
        }
        if (request.containsKey("consent_required")) {
            entity.setConsentRequired(bool(request.get("consent_required"), false));
            if (!entity.isConsentRequired()) {
                entity.setConsentStatus("NOT_REQUIRED");
            }
        }
        entity.setWorkflowStage(Math.max(entity.getWorkflowStage(), stage));
        return referralRepository.save(entity);
    }

    @Transactional
    public ReferralPackageEntity updateConsent(UUID referralId, Map<String, Object> request) {
        ReferralPackageEntity entity = getOwned(referralId);
        ensureMutable(entity);
        String consentType = str(request, "consent_type", "consentType", "type");
        if (consentType != null) {
            String normalizedType = consentType.trim().toLowerCase(Locale.ROOT);
            if (!CONSENT_TYPES.contains(normalizedType)) {
                throw new PctDomainException("INVALID_CONSENT_TYPE", 400, "unsupported consent type: " + consentType);
            }
            entity.setConsentType(normalizedType.toUpperCase(Locale.ROOT));
        }
        String consentStatus = str(request, "consent_status", "consentStatus", "status");
        if (consentStatus != null) {
            String normalizedStatus = consentStatus.trim().toLowerCase(Locale.ROOT);
            if (!CONSENT_STATUSES.contains(normalizedStatus)) {
                throw new PctDomainException("INVALID_CONSENT_STATUS", 400, "unsupported consent status: " + consentStatus);
            }
            entity.setConsentStatus(normalizedStatus.toUpperCase(Locale.ROOT));
        } else if (entity.isConsentRequired() && (entity.getConsentStatus() == null || entity.getConsentStatus().isBlank())) {
            entity.setConsentStatus("PENDING");
        }
        entity.setConsentReference(str(request, "consent_reference", "consentReference"));
        entity.setMvumoSessionId(str(request, "mvumo_session_id", "mvumoSessionId"));
        entity.setTshepoDecisionId(str(request, "tshepo_decision_id", "tshepoDecisionId"));
        entity.setWorkflowStage(Math.max(entity.getWorkflowStage(), 7));
        return referralRepository.save(entity);
    }

    @Transactional
    public ReferralPackageEntity submit(UUID referralId) {
        ReferralPackageEntity entity = getOwned(referralId);
        ensureMutable(entity);

        if (entity.getReferralLetter() == null || entity.getReferralLetter().isBlank()) {
            throw new PctDomainException("MISSING_REFERRAL_LETTER", 400, "Referral Letter stage is incomplete");
        }
        if (entity.getRoutingTarget() == null || "{}".equals(entity.getRoutingTarget())) {
            throw new PctDomainException("INVALID_ROUTING_TARGET", 400, "Routing stage is incomplete");
        }
        ensureRoutingTargetReady(entity.getRoutingTarget());
        if (entity.getVirtualMode() == null || entity.getVirtualMode().isBlank()) {
            throw new PctDomainException("INVALID_VIRTUAL_MODE", 400, "Consultation mode stage is incomplete");
        }
        if (entity.isConsentRequired()) {
            String status = entity.getConsentStatus() == null ? "" : entity.getConsentStatus().toUpperCase(Locale.ROOT);
            if (!Set.of("OBTAINED", "WAIVED", "EMERGENCY_APPROVED").contains(status)) {
                throw new PctDomainException("CONSENT_REQUIRED_MISSING", 409, "Consent is required before submitting referral");
            }
        }

        entity.setReferralPackageStatus("SUBMITTED");
        entity.setSubmittedAt(OffsetDateTime.now());
        entity.setWorkflowStage(Math.max(entity.getWorkflowStage(), 7));
        return referralRepository.save(entity);
    }

    /**
     * Records that the consultation has actually begun.
     *
     * <p>Called on the first waiting-room admission. It is idempotent by design — a consult starts
     * once, and later admissions (a second participant joining, a reconnect after a dropped line)
     * must not move the start time forward. Without this the referral model knew only when the
     * paperwork was submitted and when it was closed, so the telemedicine list could not say
     * whether a consult was under way.
     */
    @Transactional
    public ReferralPackageEntity markSessionStarted(UUID referralId, OffsetDateTime startedAt) {
        ReferralPackageEntity entity = getOwned(referralId);
        if (entity.getSessionStartedAt() == null) {
            entity.setSessionStartedAt(startedAt == null ? OffsetDateTime.now() : startedAt);
            return referralRepository.save(entity);
        }
        return entity;
    }

    /** Sets or clears the appointment time for a scheduled consult. */
    @Transactional
    public ReferralPackageEntity setSessionSchedule(UUID referralId, OffsetDateTime scheduledAt) {
        ReferralPackageEntity entity = getOwned(referralId);
        ensureMutable(entity);
        entity.setSessionScheduledAt(scheduledAt);
        return referralRepository.save(entity);
    }

    @Transactional
    public ReferralPackageEntity accept(UUID referralId, Map<String, Object> request) {
        ReferralPackageEntity entity = getOwned(referralId);
        if (!Set.of("SUBMITTED", "DRAFT").contains(entity.getReferralPackageStatus())) {
            throw new PctDomainException("INVALID_TRANSITION", 409, "Only DRAFT/SUBMITTED referrals can be accepted");
        }
        entity.setReferralPackageStatus("ACCEPTED");
        if (request != null && !request.isEmpty()) {
            entity.setRoutingTarget(normalizeJsonObject(request, entity.getRoutingTarget()));
        }
        return referralRepository.save(entity);
    }

    @Transactional
    public ReferralPackageEntity respond(UUID referralId, Map<String, Object> request) {
        ReferralPackageEntity entity = getOwned(referralId);
        if (!Set.of("SUBMITTED", "ACCEPTED").contains(entity.getReferralPackageStatus())) {
            throw new PctDomainException("INVALID_TRANSITION", 409, "Only SUBMITTED/ACCEPTED referrals can be responded");
        }
        String response = str(request, "response_notes", "responseNotes");
        if (response == null || response.isBlank()) {
            throw new PctDomainException("MISSING_RESPONSE", 400, "response_notes is required");
        }
        entity.setConsultationResponse(response);
        entity.setReferralPackageStatus("RESPONDED");
        return referralRepository.save(entity);
    }

    @Transactional
    public ReferralPackageEntity complete(UUID referralId, Map<String, Object> request) {
        ReferralPackageEntity entity = getOwned(referralId);
        if (!Set.of("RESPONDED", "ACCEPTED", "SUBMITTED").contains(entity.getReferralPackageStatus())) {
            throw new PctDomainException("INVALID_TRANSITION", 409, "Referral cannot be completed from status " + entity.getReferralPackageStatus());
        }
        entity.setReferralPackageStatus("COMPLETED");
        entity.setCompletedAt(OffsetDateTime.now());
        return referralRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public ReferralPackageEntity get(UUID referralId) {
        return getOwned(referralId);
    }

    @Transactional(readOnly = true)
    public List<ReferralPackageEntity> listByPatient(String patientId, String status) {
        TrustContext ctx = TrustContextHolder.require();
        List<ReferralPackageEntity> all = referralRepository.findByTenantIdAndPatientCpidOrderByCreatedAtDesc(ctx.tenantId(), patientId);
        return filterStatus(all, status);
    }

    @Transactional(readOnly = true)
    public List<ReferralPackageEntity> listIncoming(UUID facilityId, String status) {
        TrustContext ctx = TrustContextHolder.require();
        List<ReferralPackageEntity> all = referralRepository.findByTenantIdAndFacilityIdOrderByCreatedAtDesc(ctx.tenantId(), facilityId);
        return filterStatus(all, status);
    }

    @Transactional(readOnly = true)
    public List<ReferralPackageEntity> listByEncounter(Long encounterId) {
        TrustContext ctx = TrustContextHolder.require();
        return referralRepository.findByTenantIdAndEncounterIdOrderByCreatedAtDesc(ctx.tenantId(), encounterId);
    }

    @Transactional(readOnly = true)
    public List<ReferralPackageEntity> listOperational(String status) {
        TrustContext ctx = TrustContextHolder.require();
        List<ReferralPackageEntity> all = referralRepository.findByTenantIdOrderByCreatedAtDesc(ctx.tenantId());
        return filterStatus(all, status);
    }

    private List<ReferralPackageEntity> filterStatus(List<ReferralPackageEntity> items, String status) {
        if (status == null || status.isBlank()) return items;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return items.stream()
                .filter(item -> normalized.equals(item.getReferralPackageStatus()))
                .toList();
    }

    private ReferralPackageEntity getOwned(UUID referralId) {
        TrustContext ctx = TrustContextHolder.require();
        return referralRepository.findByTenantIdAndReferralId(ctx.tenantId(), referralId)
                .orElseThrow(() -> new PctDomainException("REFERRAL_NOT_FOUND", 404, "Referral not found: " + referralId));
    }

    private void ensureMutable(ReferralPackageEntity entity) {
        if (!ACTIVE_STATUSES.contains(entity.getReferralPackageStatus())) {
            throw new PctDomainException("INVALID_TRANSITION", 409,
                    "Referral is not mutable in status " + entity.getReferralPackageStatus());
        }
    }

    private String normalizeModality(String modality) {
        String normalized = modality == null || modality.isBlank()
                ? "virtual"
                : modality.trim().toLowerCase(Locale.ROOT);
        if (!MODALITIES.contains(normalized)) {
            throw new PctDomainException("INVALID_MODALITY", 400, "invalid modality: " + modality);
        }
        return normalized;
    }

    private String normalizeVirtualMode(String value, String modality) {
        if ("in_person".equals(modality)) {
            return null;
        }
        String normalized = value == null || value.isBlank()
                ? "video"
                : value.trim().toLowerCase(Locale.ROOT);
        if (!VIRTUAL_MODES.contains(normalized)) {
            throw new PctDomainException("INVALID_VIRTUAL_MODE", 400, "invalid virtual mode: " + value);
        }
        return normalized;
    }

    private String normalizeAttachmentDocumentIds(Object value, String fallback) {
        if (value == null) return fallback;
        List<String> references = parseAttachmentReferences(value);
        if (references.isEmpty()) return "[]";
        for (String ref : references) {
            try {
                UUID.fromString(ref);
            } catch (IllegalArgumentException ex) {
                throw new PctDomainException("INVALID_ATTACHMENT_REFERENCE", 400,
                        "attachment_document_ids must contain UUID references only");
            }
        }
        try {
            return objectMapper.writeValueAsString(references);
        } catch (JsonProcessingException e) {
            throw new PctDomainException("INVALID_ATTACHMENT_REFERENCE", 400, "attachment_document_ids must be JSON serialisable");
        }
    }

    private String normalizeRoutingTarget(Object value, String fallback) {
        if (value == null) return fallback;
        if (value instanceof String raw) {
            if (raw.isBlank()) return fallback;
            try {
                JsonNode json = objectMapper.readTree(raw);
                if (json.isObject()) {
                    return normalizeRoutingTarget(objectMapper.convertValue(json, Map.class), fallback);
                }
            } catch (JsonProcessingException ignored) {
                // keep existing behavior for non-JSON string targets
            }
            throw new PctDomainException("INVALID_ROUTING_TARGET", 400,
                    "routing_target must be an object with type and target/target_ref");
        }
        try {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> cleaned = new LinkedHashMap<>();
                map.forEach((k, v) -> {
                    if (k != null && v != null) cleaned.put(k.toString(), v);
                });
                validateRoutingTarget(cleaned);
                return objectMapper.writeValueAsString(cleaned);
            }
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new PctDomainException("INVALID_ROUTING_TARGET", 400, "routing_target must be JSON serialisable");
        }
    }

    private String normalizeJsonObject(Map<String, Object> value, String fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        Object candidate = value.containsKey("routing_target")
                ? value.get("routing_target")
                : value;
        return normalizeRoutingTarget(candidate, fallback);
    }

    private void validateRoutingTarget(Map<String, Object> target) {
        String type = target.get("type") == null ? null : target.get("type").toString().trim().toUpperCase(Locale.ROOT);
        String reference = target.get("target_ref") == null ? null : target.get("target_ref").toString().trim();
        if (reference == null || reference.isBlank()) {
            reference = target.get("target") == null ? null : target.get("target").toString().trim();
        }
        if (type == null || type.isBlank()) {
            throw new PctDomainException("INVALID_ROUTING_TARGET", 400, "routing_target.type is required");
        }
        if (!ROUTING_TYPES.contains(type)) {
            throw new PctDomainException("INVALID_ROUTING_TARGET", 400, "unsupported routing_target.type: " + type);
        }
        if (reference == null || reference.isBlank()) {
            throw new PctDomainException("INVALID_ROUTING_TARGET", 400,
                    "routing_target.target_ref (or routing_target.target) is required");
        }
    }

    private void ensureRoutingTargetReady(String routingTarget) {
        try {
            JsonNode json = objectMapper.readTree(routingTarget);
            if (!json.isObject()) {
                throw new PctDomainException("INVALID_ROUTING_TARGET", 400,
                        "routing_target must be an object with type and target/target_ref");
            }
            Map<String, Object> asMap = objectMapper.convertValue(json, Map.class);
            validateRoutingTarget(asMap);
        } catch (JsonProcessingException e) {
            throw new PctDomainException("INVALID_ROUTING_TARGET", 400, "routing_target must be valid JSON");
        }
    }

    private List<String> parseAttachmentReferences(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        if (value instanceof String raw) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) return List.of();
            try {
                JsonNode json = objectMapper.readTree(trimmed);
                if (json.isArray()) {
                    Set<String> refs = new LinkedHashSet<>();
                    for (JsonNode node : json) {
                        if (node != null && !node.isNull()) {
                            String ref = node.asText("").trim();
                            if (!ref.isBlank()) refs.add(ref);
                        }
                    }
                    return refs.stream().toList();
                }
            } catch (JsonProcessingException ignored) {
                // fallback: single/plain line references
            }
            return List.of(trimmed);
        }
        return List.of(value.toString().trim());
    }

    private Long requiredLong(Map<String, Object> src, String key, String alias) {
        String value = str(src, key, alias);
        if (value == null || value.isBlank()) {
            throw new PctDomainException("MISSING_ENCOUNTER", 400, "encounter_id is required");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new PctDomainException("INVALID_ENCOUNTER_ID", 400, "encounter_id must be numeric");
        }
    }

    private String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String str(Map<String, Object> src, String... keys) {
        for (String key : keys) {
            Object value = src.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }
}
