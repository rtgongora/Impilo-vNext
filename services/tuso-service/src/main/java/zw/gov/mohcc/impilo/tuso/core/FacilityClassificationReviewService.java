package zw.gov.mohcc.impilo.tuso.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityClassificationDtos.*;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCertificateStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileHistoryEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitCandidateEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCertificateRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileHistoryRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitCandidateRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reconciliation-workspace mutations for facility classification profiles:
 * supply facts, confirm, correct, bulk-confirm, and manage regulated-unit
 * candidates. Mutations are restricted to registry-admin actor types; a human
 * decision is never overwritten by the backfill engine.
 */
@Service
public class FacilityClassificationReviewService {

    /** Actor types permitted to mutate classification. Reads stay open like sibling endpoints. */
    static final Set<String> CLASSIFICATION_ADMIN_ROLES = Set.of("HPA_REGISTRAR", "HPA_ADMIN", "SYSTEM_ADMIN");

    /** Only genuinely-deterministic machine outcomes may be bulk-confirmed without individual review. */
    static final Set<String> BULK_CONFIRMABLE_STATUSES = Set.of(
            "DETERMINISTIC_MULTI_FIELD_MATCH", "EXACT_SOURCE_MATCH");

    private final FacilityRegulatoryProfileRepository profileRepository;
    private final FacilityRegulatoryProfileHistoryRepository historyRepository;
    private final FacilityUnitCandidateRepository candidateRepository;
    private final FacilityCertificateRepository certificateRepository;
    private final FacilityUnitService unitService;
    private final ClassificationRuleEvaluator evaluator;
    private final EventOutboxRepository outboxRepository;

    public FacilityClassificationReviewService(FacilityRegulatoryProfileRepository profileRepository,
                                               FacilityRegulatoryProfileHistoryRepository historyRepository,
                                               FacilityUnitCandidateRepository candidateRepository,
                                               FacilityCertificateRepository certificateRepository,
                                               FacilityUnitService unitService,
                                               ClassificationRuleEvaluator evaluator,
                                               EventOutboxRepository outboxRepository) {
        this.profileRepository = profileRepository;
        this.historyRepository = historyRepository;
        this.candidateRepository = candidateRepository;
        this.certificateRepository = certificateRepository;
        this.unitService = unitService;
        this.evaluator = evaluator;
        this.outboxRepository = outboxRepository;
    }

    // ── reads ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<FacilityRegulatoryProfileEntity> search(String status, String hpaClass, String canonicalType,
                                                        String sourceType, int page, int size) {
        return profileRepository.search(blankToNull(status), blankToNull(hpaClass), blankToNull(canonicalType),
                blankToNull(sourceType), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public SummaryView summary() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (Object[] row : profileRepository.countByStatus()) {
            long n = ((Number) row[1]).longValue();
            byStatus.put((String) row[0], n);
            total += n;
        }
        Map<String, Long> byClass = new LinkedHashMap<>();
        for (Object[] row : profileRepository.countByHpaClass()) {
            byClass.put((String) row[0], ((Number) row[1]).longValue());
        }
        return new SummaryView(total, byStatus, byClass);
    }

    @Transactional(readOnly = true)
    public FacilityRegulatoryProfileEntity requireProfile(Long facilityId) {
        return profileRepository.findByFacilityId(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("No classification profile for facility " + facilityId));
    }

    @Transactional(readOnly = true)
    public List<FacilityRegulatoryProfileHistoryEntity> history(Long facilityId) {
        return historyRepository.findByFacilityIdOrderByCreatedAtDesc(facilityId);
    }

    // ── mutations ────────────────────────────────────────────────────────────

    @Transactional
    public FacilityRegulatoryProfileEntity supplyFacts(TrustContext ctx, Long facilityId, FactsRequest req) {
        assertRole(ctx, "supply classification facts");
        if (req.bedCountConfirmed() == null && blank(req.careSetting())
                && blank(req.mobility()) && blank(req.specialistScope())) {
            throw new IllegalArgumentException("At least one fact must be supplied");
        }
        FacilityRegulatoryProfileEntity p = requireProfile(facilityId);
        Map<String, Object> prev = snapshot(p);
        if (req.bedCountConfirmed() != null) p.setBedCountConfirmed(req.bedCountConfirmed());
        if (!blank(req.careSetting())) p.setCareSetting(req.careSetting());
        if (!blank(req.mobility())) p.setMobility(req.mobility());
        if (!blank(req.specialistScope())) p.setSpecialistScope(req.specialistScope());

        // Re-evaluate with the supplied facts so status/label/required-facts refresh.
        ClassificationRuleEvaluator.Outcome o = evaluator.evaluate(new ClassificationRuleEvaluator.Inputs(
                p.getSourceFacilityType(), p.getSourceOwnership(), p.getBedCountClaimed(),
                p.getBedCountConfirmed(), humanCareSetting(p)));
        applyOutcome(p, o);
        profileRepository.save(p);
        recordHistory(p, "FACT_SUPPLIED", prev, snapshot(p), ctx.actorId(), req.evidenceNote());
        publish(ctx, p, "facility.classification.fact-supplied.v1");
        return p;
    }

    @Transactional
    public FacilityRegulatoryProfileEntity confirm(TrustContext ctx, Long facilityId, ConfirmRequest req) {
        assertRole(ctx, "confirm classification");
        FacilityRegulatoryProfileEntity p = requireProfile(facilityId);
        if ("NOT_APPLICABLE".equals(p.getHpaClass())) {
            if (blank(p.getHpaClassJustification())) {
                throw new IllegalArgumentException("NOT_APPLICABLE requires a justification before confirmation");
            }
        } else if (blank(p.getHpaClassificationLabel())) {
            throw new IllegalArgumentException(
                    "Cannot confirm a profile without a resolved classification label (status="
                            + p.getClassificationStatus() + ", required facts=" + p.getRequiredFacts() + ")");
        }
        Map<String, Object> prev = snapshot(p);
        p.setClassificationStatus("HUMAN_CONFIRMED");
        p.setConfirmedBy(ctx.actorId());
        p.setConfirmedAt(Instant.now());
        profileRepository.save(p);
        recordHistory(p, "HUMAN_CONFIRMED", prev, snapshot(p), ctx.actorId(), req == null ? null : req.note());
        publish(ctx, p, "facility.classification.confirmed.v1");
        return p;
    }

    @Transactional
    public FacilityRegulatoryProfileEntity correct(TrustContext ctx, Long facilityId, CorrectRequest req) {
        assertRole(ctx, "correct classification");
        FacilityRegulatoryProfileEntity p = requireProfile(facilityId);
        if (blank(req.hpaClass())) {
            throw new IllegalArgumentException("A corrected hpaClass is required");
        }
        if ("NOT_APPLICABLE".equals(req.hpaClass()) && blank(req.justification())) {
            throw new IllegalArgumentException("NOT_APPLICABLE requires a justification");
        }
        Map<String, Object> prev = snapshot(p);
        p.setHpaClass(req.hpaClass());
        p.setHpaClassificationLabel(blank(req.hpaClassificationLabel()) ? null : req.hpaClassificationLabel());
        p.setHpaClassJustification(req.justification());
        p.setClassificationStatus("HUMAN_CONFIRMED");   // a correction is a confirmed human decision
        p.setConfirmedBy(ctx.actorId());
        p.setConfirmedAt(Instant.now());

        // A reclassification of a facility that already holds a certificate needs a
        // downstream human review; we NEVER touch the certificate here.
        boolean hasActiveCert = certificateRepository
                .findFirstByFacilityIdAndStatusOrderByIssueDateDesc(facilityId, FacilityCertificateStatus.ACTIVE)
                .isPresent();
        if (hasActiveCert) {
            p.setReclassificationReviewRequired(true);
        }
        profileRepository.save(p);
        recordHistory(p, "HUMAN_CORRECTED", prev, snapshot(p), ctx.actorId(), req.note());
        publish(ctx, p, "facility.classification.corrected.v1");
        return p;
    }

    @Transactional
    public BulkConfirmResult bulkConfirm(TrustContext ctx, BulkConfirmRequest req) {
        assertRole(ctx, "bulk-confirm classification");
        List<String> requested = req == null ? List.of() : req.statuses();
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("At least one status must be supplied");
        }
        for (String s : requested) {
            if (!BULK_CONFIRMABLE_STATUSES.contains(s)) {
                throw new IllegalArgumentException("Status " + s + " is not bulk-confirmable; only "
                        + BULK_CONFIRMABLE_STATUSES + " may be confirmed in bulk");
            }
        }
        int confirmed = 0;
        for (String status : requested) {
            for (FacilityRegulatoryProfileEntity p : profileRepository.findByClassificationStatus(status)) {
                if (blank(p.getHpaClassificationLabel())) continue; // defensive: never confirm an unlabelled row
                Map<String, Object> prev = snapshot(p);
                p.setClassificationStatus("HUMAN_CONFIRMED");
                p.setConfirmedBy(ctx.actorId());
                p.setConfirmedAt(Instant.now());
                profileRepository.save(p);
                recordHistory(p, "BULK_CONFIRMED", prev, snapshot(p), ctx.actorId(), "bulk-confirm " + status);
                confirmed++;
            }
        }
        return new BulkConfirmResult(confirmed, requested);
    }

    // ── unit candidates ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FacilityUnitCandidateEntity> listCandidates(Long facilityId) {
        return candidateRepository.findByFacilityIdOrderByCreatedAtDesc(facilityId);
    }

    @Transactional
    public FacilityUnitCandidateEntity proposeCandidate(TrustContext ctx, Long facilityId, UnitCandidateRequest req) {
        assertRole(ctx, "propose a regulated-unit candidate");
        if (blank(req.suggestedUnitType())) {
            throw new IllegalArgumentException("suggestedUnitType is required");
        }
        FacilityUnitCandidateEntity c = new FacilityUnitCandidateEntity();
        c.setTenantId(ctx.tenantId());
        c.setFacilityId(facilityId);
        c.setSuggestedUnitType(req.suggestedUnitType());
        c.setOrigin("HUMAN_PROPOSED");            // never PARENT_TYPE_INFERENCE
        c.setServicePresence(blank(req.evidenceRef()) ? "UNKNOWN" : "EVIDENCED");
        c.setStatus("PROPOSED");
        c.setRationale(req.rationale());
        c.setEvidenceRef(req.evidenceRef());
        c.setProposedBy(ctx.actorId());
        return candidateRepository.save(c);
    }

    @Transactional
    public FacilityUnitCandidateEntity confirmCandidate(TrustContext ctx, UUID candidateId, UnitCandidateConfirmRequest req) {
        assertRole(ctx, "confirm a regulated-unit candidate");
        FacilityUnitCandidateEntity c = candidateRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Unit candidate not found: " + candidateId));
        if (!"PROPOSED".equals(c.getStatus())) {
            throw new IllegalArgumentException("Only a PROPOSED candidate can be confirmed (was " + c.getStatus() + ")");
        }
        // Create the REAL regulated unit through the existing service — a regulated
        // unit requires registration + inspection before use, hence registrationRequired=true.
        boolean picRequired = req != null && Boolean.TRUE.equals(req.picRequired());
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("picRequired", picRequired);
        meta.put("createdFromCandidate", candidateId.toString());
        FacilityUnitEntity unit = unitService.create(ctx, c.getFacilityId(),
                new FacilityUnitService.CreateUnitRequest(
                        req != null && !blank(req.name()) ? req.name() : c.getSuggestedUnitType(),
                        c.getSuggestedUnitType(),
                        req == null ? null : req.serviceLine(),
                        true, meta));
        c.setStatus("CONFIRMED");
        c.setCreatedUnitId(unit.getId());
        c.setDecidedBy(ctx.actorId());
        c.setDecidedAt(Instant.now());
        candidateRepository.save(c);
        publishUnitCandidate(ctx, c, "facility.unit-candidate.confirmed.v1");
        return c;
    }

    @Transactional
    public FacilityUnitCandidateEntity rejectCandidate(TrustContext ctx, UUID candidateId) {
        assertRole(ctx, "reject a regulated-unit candidate");
        FacilityUnitCandidateEntity c = candidateRepository.findByCandidateId(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Unit candidate not found: " + candidateId));
        c.setStatus("REJECTED");
        c.setDecidedBy(ctx.actorId());
        c.setDecidedAt(Instant.now());
        return candidateRepository.save(c);
    }

    @Transactional
    public FacilityRegulatoryProfileEntity completeUnitReview(TrustContext ctx, Long facilityId) {
        assertRole(ctx, "complete unit review");
        FacilityRegulatoryProfileEntity p = requireProfile(facilityId);
        p.setUnitsReviewStatus("COMPLETED");
        profileRepository.save(p);
        recordHistory(p, "UNIT_REVIEW_COMPLETED", null, snapshot(p), ctx.actorId(), null);
        return p;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private void applyOutcome(FacilityRegulatoryProfileEntity p, ClassificationRuleEvaluator.Outcome o) {
        p.setCanonicalType(o.canonicalType());
        p.setOwnershipAuthority(o.ownershipAuthority());
        p.setHpaRoute(o.hpaRoute());
        p.setCareSetting(o.careSetting());
        p.setHpaClass(o.hpaClass());
        p.setHpaClassificationLabel(o.hpaClassificationLabel());
        p.setClassificationStatus(o.classificationStatus());
        p.setDimensionStatuses(o.dimensionStatuses());
        p.setSuggestion(o.suggestion());
        p.setRequiredFacts(o.requiredFacts());
        p.setSourceFieldsUsed(o.sourceFieldsUsed());
        p.setBedBand(o.bedBand());
        p.setBedBandBasis(o.bedBandBasis());
        p.setMappingRuleCode(o.ruleCode());
        p.setMappingRuleVersion(o.ruleVersion());
        p.setEvaluationFingerprint(o.evaluationFingerprint());
    }

    private String humanCareSetting(FacilityRegulatoryProfileEntity p) {
        String cs = p.getCareSetting();
        return (cs == null || cs.isBlank() || "UNKNOWN".equals(cs)) ? null : cs;
    }

    private void recordHistory(FacilityRegulatoryProfileEntity p, String kind, Map<String, Object> prev,
                               Map<String, Object> next, String actor, String note) {
        FacilityRegulatoryProfileHistoryEntity h = new FacilityRegulatoryProfileHistoryEntity();
        h.setProfileId(p.getProfileId());
        h.setFacilityId(p.getFacilityId());
        h.setTenantId(p.getTenantId());
        h.setChangeKind(kind);
        h.setPrevSnapshot(prev);
        h.setNewSnapshot(next);
        h.setActor(actor);
        h.setNote(note);
        historyRepository.save(h);
    }

    private Map<String, Object> snapshot(FacilityRegulatoryProfileEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canonicalType", p.getCanonicalType());
        m.put("hpaClass", p.getHpaClass());
        m.put("hpaClassificationLabel", p.getHpaClassificationLabel());
        m.put("classificationStatus", p.getClassificationStatus());
        m.put("careSetting", p.getCareSetting());
        m.put("bedBand", p.getBedBand());
        return m;
    }

    private void publish(TrustContext ctx, FacilityRegulatoryProfileEntity p, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facilityId", p.getFacilityId());
        payload.put("classificationStatus", p.getClassificationStatus());
        payload.put("hpaClass", p.getHpaClass());
        payload.put("hpaClassificationLabel", p.getHpaClassificationLabel());
        emit(ctx, "FACILITY", String.valueOf(p.getFacilityId()), eventType, payload,
                "tuso:classification:" + eventType + ":" + p.getFacilityId());
    }

    private void publishUnitCandidate(TrustContext ctx, FacilityUnitCandidateEntity c, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("facilityId", c.getFacilityId());
        payload.put("candidateId", c.getCandidateId().toString());
        payload.put("unitType", c.getSuggestedUnitType());
        payload.put("createdUnitId", c.getCreatedUnitId());
        emit(ctx, "FACILITY", String.valueOf(c.getFacilityId()), eventType, payload,
                "tuso:unit-candidate:" + eventType + ":" + c.getCandidateId());
    }

    private void emit(TrustContext ctx, String aggregateType, String aggregateId, String eventType,
                      Map<String, Object> payload, String idempotencyKey) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(ctx.tenantId().toString());
        event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        event.setProducer("tuso");
        event.setSubjectType("Facility");
        event.setSubjectId(aggregateId);
        event.setPartitionKey(aggregateId);
        event.setSchemaVersion(1);
        event.setPodId("national-spine");
        event.setIdempotencyKey(idempotencyKey + ":" + UUID.randomUUID());
        outboxRepository.save(event);
    }

    private void assertRole(TrustContext ctx, String action) {
        String actorType = ctx.actorType();
        if (actorType == null || actorType.isBlank()) {
            return; // permissive when unset, matching the estate idiom (ext_authz already gated the call)
        }
        if (!CLASSIFICATION_ADMIN_ROLES.contains(actorType)) {
            throw new SecurityException("Actor type " + actorType + " cannot " + action);
        }
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String blankToNull(String s) { return blank(s) ? null : s; }
}
