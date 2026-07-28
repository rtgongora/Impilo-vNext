package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Ministry reasserting recognition of facilities already on its own master list (FCV-W3).
 *
 * <p>The 1,774 Ministry facilities were loaded by seed migration V027, not through the importer, so
 * they carry no source-legitimacy rows at all — and because the composite treats silence as a
 * refusal, even real referral hospitals resolve as not operational. The seed said this would happen:
 * "per-facility legitimacy/classification is a separate governed enrichment". This is that
 * enrichment.</p>
 *
 * <p><b>Recognition is not operation.</b> The batch writes {@code MINISTRY_OPERATIONAL} — this
 * facility is on the national master list — and deliberately leaves {@code PLATFORM_OPERATIONAL}
 * denying. Every facility it touches therefore stays {@code operational=false} until a verifier
 * decides on it individually. A bulk act must never become a bulk opening, and this is the line
 * between "the Ministry knows this facility exists" and "the platform will let it operate".</p>
 *
 * <p><b>Why a batch and not an UPDATE.</b> Asserting that the Ministry recognises a facility is a
 * statement somebody makes, so it carries a named authority, a reason, a dry run, per-facility
 * outcomes and a reversal. A SQL statement would carry none of that and would bypass the
 * denier-present invariant besides.</p>
 */
@Service
public class MinistryRecognitionBatchService {

    private static final Logger log = LoggerFactory.getLogger(MinistryRecognitionBatchService.class);

    /**
     * Candidates: facilities on the national master list that carry NO legitimacy verdict at all.
     * A facility that already has any verdict is left alone — this batch fills silence, it does not
     * overwrite anyone's judgement (least of all a governed decision, which V044 protects anyway).
     */
    private static final String CANDIDATES_SQL =
            "SELECT f.facility_uuid, f.facility_code FROM tuso.facility f "
                    + "WHERE f.tenant_id = ? "
                    + "  AND f.facility_code IS NOT NULL AND btrim(f.facility_code) <> '' "
                    + "  AND f.regulatory_status <> 'IMPORTED_PENDING_CONFIGURATION' "
                    + "  AND NOT EXISTS (SELECT 1 FROM tuso.facility_source_legitimacy l "
                    + "                   WHERE l.facility_id = f.facility_uuid) "
                    + "ORDER BY f.id LIMIT ?";

    private final JdbcTemplate jdbc;
    private final FacilityRepository facilityRepository;
    private final FacilitySourceLegitimacyRepository legitimacyRepository;
    private final MinistryAuthorityService ministryAuthority;

    public MinistryRecognitionBatchService(JdbcTemplate jdbc,
                                           FacilityRepository facilityRepository,
                                           FacilitySourceLegitimacyRepository legitimacyRepository,
                                           MinistryAuthorityService ministryAuthority) {
        this.jdbc = jdbc;
        this.facilityRepository = facilityRepository;
        this.legitimacyRepository = legitimacyRepository;
        this.ministryAuthority = ministryAuthority;
    }

    public record BatchResult(UUID batchId, int scanned, int recognised, int skipped,
                              boolean dryRun, int remaining) {}

    /**
     * Assert Ministry recognition over a bounded batch of unstamped master-list facilities.
     *
     * @param dryRun when true nothing is written beyond the batch record itself — the counts are
     *               the preview a Ministry administrator approves before the real run
     */
    @Transactional
    public BatchResult assertRecognition(String sourceLabel, String reason, int limit, boolean dryRun) {
        TrustContext ctx = TrustContextHolder.require();
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A reason is required — a bulk assertion of Ministry recognition must say on whose "
                            + "authority and on what basis it is made.");
        }
        if (sourceLabel == null || sourceLabel.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "sourceLabel is required — name the master list being reasserted.");
        }

        // Authority: running a governed registry batch is the registry-administrator duty, not the
        // verifier duty. Deliberately a different role from the one that decides a facility is
        // operational — the person who can bulk-assert recognition must not thereby be able to open
        // facilities, and the two acts stay separable.
        FacilityEntity any = facilityRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
        MinistryAuthorityService.Authority authority = (any == null ? java.util.Optional
                .<MinistryAuthorityService.Authority>empty()
                : ministryAuthority.authorityOver(ctx.actorId(), any,
                        MinistryAuthorityService.REGISTRY_ADMIN_ROLES))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Asserting Ministry recognition in bulk requires an active MoHCC registry "
                                + "administrator appointment."));

        int batch = Math.min(Math.max(limit, 1), 1000);
        List<Map<String, Object>> candidates = jdbc.queryForList(CANDIDATES_SQL, ctx.tenantId(), batch);

        UUID batchId = UUID.randomUUID();
        jdbc.update("INSERT INTO tuso.ministry_recognition_batch ("
                        + "id, tenant_id, asserted_by, asserted_by_role, jurisdiction_code, source_label, "
                        + "reason, dry_run, scanned, recognised, skipped, correlation_id) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                batchId, ctx.tenantId(), ctx.actorId(), authority.roleCode(),
                authority.jurisdictionCode(), sourceLabel.trim(), reason.trim(), dryRun,
                candidates.size(), 0, 0,
                ctx.correlationId() == null ? null : ctx.correlationId().toString());

        int recognised = 0;
        int skipped = 0;
        for (Map<String, Object> row : candidates) {
            UUID facilityUuid = (UUID) row.get("facility_uuid");
            String facilityCode = (String) row.get("facility_code");
            if (dryRun) {
                recordItem(batchId, facilityUuid, facilityCode, "RECOGNISED",
                        "dry run — nothing written");
                recognised++;
                continue;
            }
            FacilityEntity facility = facilityRepository.findByFacilityUuid(facilityUuid).orElse(null);
            if (facility == null) {
                skipped++;
                continue;
            }
            stampRecognition(facility, batchId, sourceLabel.trim(), ctx.actorId());
            recordItem(batchId, facilityUuid, facilityCode, "RECOGNISED", null);
            recognised++;
        }

        jdbc.update("UPDATE tuso.ministry_recognition_batch SET recognised=?, skipped=? WHERE id=?",
                recognised, skipped, batchId);

        int remaining = countRemaining(ctx.tenantId());
        log.info("Ministry recognition batch {} ({}): scanned={} recognised={} skipped={} remaining={}",
                batchId, dryRun ? "DRY RUN" : "APPLIED", candidates.size(), recognised, skipped, remaining);
        return new BatchResult(batchId, candidates.size(), recognised, skipped, dryRun, remaining);
    }

    /**
     * Write the two rows: the Ministry's recognition, and the platform's standing refusal.
     *
     * <p>The platform row is written FIRST and always denies. It is what keeps this batch from
     * operationalising the estate — and it satisfies V044's invariant, which refuses a Ministry
     * allow with no platform verdict on record precisely so this cannot be got wrong.</p>
     */
    private void stampRecognition(FacilityEntity facility, UUID batchId, String sourceLabel, String actorId) {
        upsert(facility, FacilityLegitimacySource.PLATFORM_OPERATIONAL,
                FacilityLegitimacyStatus.PENDING_VERIFICATION, false, actorId,
                "Recognised on the national master list; the platform has not verified that this "
                        + "facility is operating. Awaiting a Ministry verifier decision.",
                "MINISTRY_RECOGNITION_BATCH:" + batchId);
        upsert(facility, FacilityLegitimacySource.MINISTRY_OPERATIONAL,
                FacilityLegitimacyStatus.REGISTERED_CURRENT, true, actorId,
                "Recognised by the Ministry of Health and Child Care on the national master facility "
                        + "list (" + sourceLabel + ").",
                "MINISTRY_RECOGNITION_BATCH:" + batchId);
    }

    private void upsert(FacilityEntity facility, FacilityLegitimacySource source,
                        FacilityLegitimacyStatus status, boolean allowed, String actorId,
                        String reason, String evidenceRef) {
        FacilitySourceLegitimacyEntity row = legitimacyRepository
                .findByFacilityIdAndSource(facility.getFacilityUuid(), source)
                .orElseGet(FacilitySourceLegitimacyEntity::new);
        if (row.getDecisionId() != null) {
            return; // a governed decision owns this row; a bulk assertion never overrides one
        }
        row.setFacilityId(facility.getFacilityUuid());
        row.setSource(source);
        row.setStatus(status);
        row.setAsOf(Instant.now());
        row.setAllowedOnPlatform(allowed);
        row.setEvidenceRef(evidenceRef);
        row.setReason(reason);
        row.setRecordedBy(actorId);
        legitimacyRepository.save(row);
    }

    private void recordItem(UUID batchId, UUID facilityUuid, String facilityCode,
                            String outcome, String note) {
        jdbc.update("INSERT INTO tuso.ministry_recognition_batch_item "
                        + "(batch_id, facility_uuid, facility_code, outcome, note) VALUES (?,?,?,?,?) "
                        + "ON CONFLICT (batch_id, facility_uuid) DO NOTHING",
                batchId, facilityUuid, facilityCode, outcome, note);
    }

    /**
     * Undo a batch: remove the rows it wrote, for the facilities it touched, leaving anything a
     * governed decision has since claimed alone. A bulk act that cannot be undone should not be run.
     */
    @Transactional
    public int revert(UUID batchId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason is required to revert a batch.");
        }
        Integer alreadyReverted = jdbc.queryForObject(
                "SELECT count(*) FROM tuso.ministry_recognition_batch WHERE id=? AND status='REVERTED'",
                Integer.class, batchId);
        if (alreadyReverted != null && alreadyReverted > 0) {
            return 0; // idempotent
        }
        // MINISTRY row first: dropping it before the platform row never leaves a Ministry allow
        // standing without its denier, which V044's trigger would refuse anyway.
        int removed = jdbc.update(
                "DELETE FROM tuso.facility_source_legitimacy l "
                        + "USING tuso.ministry_recognition_batch_item i "
                        + "WHERE i.batch_id = ? AND l.facility_id = i.facility_uuid "
                        + "  AND l.source = 'MINISTRY_OPERATIONAL' AND l.decision_id IS NULL "
                        + "  AND l.evidence_ref = ?",
                batchId, "MINISTRY_RECOGNITION_BATCH:" + batchId);
        removed += jdbc.update(
                "DELETE FROM tuso.facility_source_legitimacy l "
                        + "USING tuso.ministry_recognition_batch_item i "
                        + "WHERE i.batch_id = ? AND l.facility_id = i.facility_uuid "
                        + "  AND l.source = 'PLATFORM_OPERATIONAL' AND l.decision_id IS NULL "
                        + "  AND l.evidence_ref = ?",
                batchId, "MINISTRY_RECOGNITION_BATCH:" + batchId);

        jdbc.update("UPDATE tuso.ministry_recognition_batch SET status='REVERTED', reverted_at=now(), "
                        + "reverted_by=?, revert_reason=? WHERE id=?",
                ctx.actorId(), reason.trim(), batchId);
        log.info("Ministry recognition batch {} REVERTED by {} — {} legitimacy rows removed",
                batchId, ctx.actorId(), removed);
        return removed;
    }

    /** How many master-list facilities still carry no verdict at all. */
    @Transactional(readOnly = true)
    public int countRemaining(UUID tenantId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM tuso.facility f WHERE f.tenant_id = ? "
                        + "AND f.facility_code IS NOT NULL AND btrim(f.facility_code) <> '' "
                        + "AND f.regulatory_status <> 'IMPORTED_PENDING_CONFIGURATION' "
                        + "AND NOT EXISTS (SELECT 1 FROM tuso.facility_source_legitimacy l "
                        + "                 WHERE l.facility_id = f.facility_uuid)",
                Integer.class, tenantId);
        return n == null ? 0 : n;
    }
}
