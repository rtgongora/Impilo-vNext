package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEnrichmentRunEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileHistoryEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityEnrichmentRunRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileHistoryRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Idempotent classification-enrichment backfill: reconciles the imported
 * national facility estate against the HPA classification model without
 * mutating imported source truth or creating facilities. Mirrors the
 * {@code FacilityMasterImportService} run pattern — a versioned run row with
 * verified estate totals, per-status counts, and an exception report.
 *
 * <p>Preservation invariant: a HUMAN_CONFIRMED or REJECTED_OR_CORRECTED profile
 * is NEVER overwritten by a re-run; if the fresh machine outcome would differ,
 * the drift is recorded (history + exception) rather than forced. Human-supplied
 * facts (confirmed bed count, care setting) are inputs to re-evaluation, never
 * cleared. Fingerprint no-ops keep re-runs cheap and non-churning.</p>
 */
@Service
public class FacilityClassificationEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(FacilityClassificationEnrichmentService.class);
    private static final int PAGE = 500;

    private final FacilityRepository facilityRepository;
    private final FacilityRegulatoryProfileRepository profileRepository;
    private final FacilityRegulatoryProfileHistoryRepository historyRepository;
    private final FacilityEnrichmentRunRepository runRepository;
    private final ClassificationRuleEvaluator evaluator;
    private final EventOutboxRepository outboxRepository;

    public FacilityClassificationEnrichmentService(FacilityRepository facilityRepository,
                                                   FacilityRegulatoryProfileRepository profileRepository,
                                                   FacilityRegulatoryProfileHistoryRepository historyRepository,
                                                   FacilityEnrichmentRunRepository runRepository,
                                                   ClassificationRuleEvaluator evaluator,
                                                   EventOutboxRepository outboxRepository) {
        this.facilityRepository = facilityRepository;
        this.profileRepository = profileRepository;
        this.historyRepository = historyRepository;
        this.runRepository = runRepository;
        this.evaluator = evaluator;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public FacilityEnrichmentRunEntity runBackfill(TrustContext ctx, boolean dryRun) {
        UUID tenantId = ctx.tenantId();
        FacilityEnrichmentRunEntity run = new FacilityEnrichmentRunEntity();
        run.setTenantId(tenantId);
        run.setDryRun(dryRun);
        run.setInitiatedBy(ctx.actorId());
        run.setStatus("RUNNING");
        run = runRepository.save(run);

        int total = 0, evaluated = 0, created = 0, updated = 0, unchanged = 0, preserved = 0, failed = 0;
        Map<String, Integer> byStatus = new TreeMap<>();
        Map<String, Integer> byType = new TreeMap<>();
        Map<String, Integer> byOwnership = new TreeMap<>();
        List<Map<String, Object>> exceptions = new ArrayList<>();
        String ruleCode = null;
        Integer ruleVersion = null;

        int pageIndex = 0;
        Page<FacilityEntity> page;
        do {
            page = facilityRepository.findByTenantId(tenantId, PageRequest.of(pageIndex, PAGE));
            for (FacilityEntity f : page.getContent()) {
                total++;
                tally(byType, f.getFacilityType());
                tally(byOwnership, f.getOwnership());
                try {
                    Optional<FacilityRegulatoryProfileEntity> existingOpt =
                            profileRepository.findByFacilityId(f.getId());

                    Integer bedClaimed = readBedCapacity(f, exceptions);
                    FacilityRegulatoryProfileEntity existing = existingOpt.orElse(null);
                    Integer bedConfirmed = existing != null ? existing.getBedCountConfirmed() : null;
                    String humanCareSetting = humanCareSetting(existing);

                    ClassificationRuleEvaluator.Outcome outcome = evaluator.evaluate(
                            new ClassificationRuleEvaluator.Inputs(
                                    f.getFacilityType(), f.getOwnership(), bedClaimed, bedConfirmed, humanCareSetting));
                    ruleCode = outcome.ruleCode();
                    ruleVersion = outcome.ruleVersion();
                    evaluated++;
                    if (!outcome.exceptions().isEmpty()) {
                        for (Map<String, Object> e : outcome.exceptions()) {
                            Map<String, Object> withFacility = new LinkedHashMap<>(e);
                            withFacility.put("facilityId", f.getId());
                            exceptions.add(withFacility);
                        }
                    }
                    tally(byStatus, resolvedStatus(existing, outcome));

                    if (dryRun) {
                        continue;
                    }

                    if (existing == null) {
                        FacilityRegulatoryProfileEntity profile = new FacilityRegulatoryProfileEntity();
                        profile.setTenantId(tenantId);
                        profile.setFacilityId(f.getId());
                        profile.setSourceFacilityType(f.getFacilityType());
                        profile.setSourceOwnership(f.getOwnership());
                        profile.setSourceLevel(f.getLevel());
                        profile.setSourceBedCapacity(rawBedCapacity(f));
                        profile.setBedCountClaimed(bedClaimed);
                        applyOutcome(profile, outcome);
                        profileRepository.save(profile);
                        recordHistory(profile, "BACKFILL_CREATED", null, snapshot(profile), ctx.actorId(), run.getId(),
                                null);
                        created++;
                    } else if (isHumanLocked(existing)) {
                        if (machineOutcomeDiverges(existing, outcome)) {
                            Map<String, Object> drift = new LinkedHashMap<>();
                            drift.put("code", "HUMAN_CONFIRMED_MACHINE_DRIFT");
                            drift.put("detail", "backfill outcome (" + outcome.classificationStatus()
                                    + "/" + outcome.hpaClassificationLabel() + ") differs from the human-confirmed value");
                            drift.put("facilityId", f.getId());
                            exceptions.add(drift);
                            recordHistory(existing, "BACKFILL_PRESERVED_HUMAN", snapshot(existing), snapshot(existing),
                                    ctx.actorId(), run.getId(), "machine outcome diverged; human value preserved");
                        }
                        preserved++;
                    } else if (existing.getEvaluationFingerprint() != null
                            && existing.getEvaluationFingerprint().equals(outcome.evaluationFingerprint())) {
                        unchanged++;
                    } else {
                        Map<String, Object> prev = snapshot(existing);
                        existing.setBedCountClaimed(bedClaimed);
                        applyOutcome(existing, outcome);
                        profileRepository.save(existing);
                        recordHistory(existing, "BACKFILL_UPDATED", prev, snapshot(existing), ctx.actorId(),
                                run.getId(), null);
                        updated++;
                    }
                } catch (Exception ex) {
                    failed++;
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("code", "EVALUATION_FAILED");
                    e.put("detail", ex.getMessage());
                    e.put("facilityId", f.getId());
                    exceptions.add(e);
                    log.warn("Classification enrichment failed for facility {}: {}", f.getId(), ex.getMessage());
                }
            }
            pageIndex++;
        } while (page.hasNext());

        Map<String, Object> estateAudit = new LinkedHashMap<>();
        estateAudit.put("verifiedFacilityCount", total);
        estateAudit.put("byFacilityType", byType);
        estateAudit.put("byOwnership", byOwnership);

        run.setRecordsTotal(total);
        run.setRecordsEvaluated(evaluated);
        run.setRecordsCreated(created);
        run.setRecordsUpdated(updated);
        run.setRecordsUnchanged(unchanged);
        run.setRecordsPreservedHuman(preserved);
        run.setRecordsFailed(failed);
        run.setCountsByStatus(new LinkedHashMap<>(byStatus));
        run.setEstateAudit(estateAudit);
        run.setExceptionReport(exceptions);
        run.setMappingRuleCode(ruleCode);
        run.setMappingRuleVersion(ruleVersion);
        run.setStatus("COMPLETED");
        run.setCompletedAt(Instant.now());
        run = runRepository.save(run);

        publishRunCompleted(ctx, run);
        log.info("Classification enrichment run {} complete: total={} created={} updated={} unchanged={} "
                        + "preservedHuman={} failed={} dryRun={}",
                run.getId(), total, created, updated, unchanged, preserved, failed, dryRun);
        return run;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Reads bed_capacity from imported facility metadata; non-numeric → absent + exception. */
    private Integer readBedCapacity(FacilityEntity f, List<Map<String, Object>> exceptions) {
        Map<String, Object> md = f.getMetadata();
        if (md == null) return null;
        Object raw = md.get("bed_capacity");
        if (raw == null) return null;
        if (raw instanceof Number n) return n.intValue();
        String s = raw.toString().trim();
        if (s.isBlank()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("code", "NON_NUMERIC_BED_CAPACITY");
            e.put("detail", "bed_capacity '" + s + "' is not an integer; treated as absent");
            e.put("facilityId", f.getId());
            exceptions.add(e);
            return null;
        }
    }

    private String rawBedCapacity(FacilityEntity f) {
        Map<String, Object> md = f.getMetadata();
        Object raw = md == null ? null : md.get("bed_capacity");
        return raw == null ? null : raw.toString();
    }

    /** A profile's care setting is only a human input to re-evaluation when it was supplied (non-UNKNOWN). */
    private String humanCareSetting(FacilityRegulatoryProfileEntity existing) {
        if (existing == null) return null;
        String cs = existing.getCareSetting();
        return (cs == null || cs.isBlank() || "UNKNOWN".equals(cs)) ? null : cs;
    }

    private boolean isHumanLocked(FacilityRegulatoryProfileEntity p) {
        return "HUMAN_CONFIRMED".equals(p.getClassificationStatus())
                || "REJECTED_OR_CORRECTED".equals(p.getClassificationStatus());
    }

    private boolean machineOutcomeDiverges(FacilityRegulatoryProfileEntity p, ClassificationRuleEvaluator.Outcome o) {
        return !java.util.Objects.equals(p.getHpaClassificationLabel(), o.hpaClassificationLabel())
                || !java.util.Objects.equals(p.getHpaClass(), o.hpaClass());
    }

    private String resolvedStatus(FacilityRegulatoryProfileEntity existing,
                                  ClassificationRuleEvaluator.Outcome outcome) {
        // Human-locked rows keep their status in the dashboard; machine rows report the fresh outcome.
        return (existing != null && isHumanLocked(existing)) ? existing.getClassificationStatus()
                : outcome.classificationStatus();
    }

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
        if (o.unitsReviewRequired() && "NOT_REQUIRED".equals(p.getUnitsReviewStatus())) {
            p.setUnitsReviewStatus("REQUIRED");
        }
    }

    private void recordHistory(FacilityRegulatoryProfileEntity p, String kind, Map<String, Object> prev,
                               Map<String, Object> next, String actor, Long runId, String note) {
        FacilityRegulatoryProfileHistoryEntity h = new FacilityRegulatoryProfileHistoryEntity();
        h.setProfileId(p.getProfileId());
        h.setFacilityId(p.getFacilityId());
        h.setTenantId(p.getTenantId());
        h.setChangeKind(kind);
        h.setPrevSnapshot(prev);
        h.setNewSnapshot(next);
        h.setActor(actor);
        h.setRunId(runId);
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

    private void tally(Map<String, Integer> counts, String key) {
        String k = (key == null || key.isBlank()) ? "(blank)" : key;
        counts.merge(k, 1, Integer::sum);
    }

    private void publishRunCompleted(TrustContext ctx, FacilityEnrichmentRunEntity run) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", run.getId());
        payload.put("dryRun", run.isDryRun());
        payload.put("recordsTotal", run.getRecordsTotal());
        payload.put("recordsCreated", run.getRecordsCreated());
        payload.put("recordsUpdated", run.getRecordsUpdated());
        payload.put("countsByStatus", run.getCountsByStatus());
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FACILITY_CLASSIFICATION_RUN");
        event.setAggregateId(String.valueOf(run.getId()));
        event.setEventType("facility.classification.enrichment-run.completed.v1");
        event.setPayload(payload);
        event.setTenantId(ctx.tenantId().toString());
        event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        event.setProducer("tuso");
        event.setSubjectType("FacilityEnrichmentRun");
        event.setSubjectId(String.valueOf(run.getId()));
        event.setPartitionKey(String.valueOf(run.getId()));
        event.setSchemaVersion(1);
        event.setPodId("national-spine");
        event.setIdempotencyKey("tuso:classification:run-completed:" + run.getId());
        outboxRepository.save(event);
    }
}
