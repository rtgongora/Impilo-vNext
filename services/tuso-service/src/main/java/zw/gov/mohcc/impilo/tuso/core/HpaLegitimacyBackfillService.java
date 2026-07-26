package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backfills the source-legitimacy verdicts for facilities that the HPA import created before the
 * importer stamped them (HAR W1).
 *
 * <p>Why a service and not a SQL migration: a raw INSERT into {@code facility_source_legitimacy}
 * bypasses {@code FacilitySourceLegitimacyService.stampFromImport}, so no outbox event is published
 * and no audit trail is written — the platform would gain a legitimacy verdict that nothing
 * downstream ever heard about. Every production write needs authz, audit and observability.</p>
 *
 * <p>Paced and resumable: a bounded batch per call, committed per facility, and idempotent by
 * {@code (facility, source)} inside {@code stampFromImport}. Re-running is safe and cheap.</p>
 */
@Service
public class HpaLegitimacyBackfillService {

    private static final Logger log = LoggerFactory.getLogger(HpaLegitimacyBackfillService.class);

    /** The HPA regulatory_status per facility, as asserted at import time. */
    private static final String ASSERTED_STATUS_SQL =
            "SELECT asserted_value FROM tuso.facility_field_assertion "
                    + "WHERE facility_id = ? AND field_path = 'regulatory.status' "
                    + "ORDER BY id DESC LIMIT 1";

    /** Facilities the HPA import created that still carry no legitimacy verdict at all. */
    private static final String CANDIDATES_SQL =
            "SELECT f.id FROM tuso.facility f "
                    + "WHERE f.tenant_id = ? "
                    + "  AND f.regulatory_status = 'IMPORTED_PENDING_CONFIGURATION' "
                    + "  AND EXISTS (SELECT 1 FROM tuso.facility_identifier i "
                    + "              WHERE i.facility_id = f.id AND i.system = 'HPA_INSTITUTION_ID') "
                    + "  AND NOT EXISTS (SELECT 1 FROM tuso.facility_source_legitimacy l "
                    + "                  WHERE l.facility_id = f.facility_uuid) "
                    + "ORDER BY f.id LIMIT ?";

    private final JdbcTemplate jdbc;
    private final FacilityRepository facilityRepository;
    private final FacilitySourceLegitimacyService sourceLegitimacyService;
    private final TransactionTemplate txTemplate;

    public HpaLegitimacyBackfillService(JdbcTemplate jdbc,
                                        FacilityRepository facilityRepository,
                                        FacilitySourceLegitimacyService sourceLegitimacyService,
                                        PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.facilityRepository = facilityRepository;
        this.sourceLegitimacyService = sourceLegitimacyService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public record BackfillResult(int scanned, int stamped, int skipped, boolean dryRun, int remaining) {}

    /**
     * Stamp HPA_LEGAL + PLATFORM_OPERATIONAL for up to {@code limit} un-stamped HPA facilities.
     *
     * @param dryRun when true, reports what would be stamped and writes nothing
     */
    public BackfillResult backfill(UUID tenantId, String actorId, int limit, boolean dryRun) {
        int batch = Math.min(Math.max(limit, 1), 1000);
        List<Long> ids = jdbc.queryForList(CANDIDATES_SQL, Long.class, tenantId, batch);

        int stamped = 0;
        int skipped = 0;
        for (Long facilityId : ids) {
            if (dryRun) {
                stamped++;
                continue;
            }
            try {
                Boolean done = txTemplate.execute(status -> stampOne(facilityId, tenantId, actorId));
                if (Boolean.TRUE.equals(done)) {
                    stamped++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                skipped++;
                log.warn("HAR W1 legitimacy backfill failed for facility {}: {}", facilityId, e.getMessage());
            }
        }

        int remaining = countRemaining(tenantId);
        log.info("HAR W1 legitimacy backfill: scanned={} stamped={} skipped={} dryRun={} remaining={}",
                ids.size(), stamped, skipped, dryRun, remaining);
        return new BackfillResult(ids.size(), stamped, skipped, dryRun, remaining);
    }

    private boolean stampOne(Long facilityId, UUID tenantId, String actorId) {
        FacilityEntity facility = facilityRepository.findById(facilityId).orElse(null);
        if (facility == null || facility.getFacilityUuid() == null) {
            return false;
        }
        String assertedStatus = assertedRegulatoryStatus(facilityId);
        FacilityLegitimacyStatus hpaStatus = HpaEnrichmentImportService.hpaLegitimacyStatus(assertedStatus);
        String regNum = identifierValue(facilityId, HpaEnrichmentImportService.SYS_HPA_REGISTRATION_NUMBER);

        sourceLegitimacyService.stampFromImport(facility,
                FacilityLegitimacySource.HPA_LEGAL,
                hpaStatus,
                hpaStatus == FacilityLegitimacyStatus.REGISTERED_CURRENT,
                regNum == null
                        ? HpaEnrichmentImportService.SYS_HPA_INSTITUTION_ID + ":" + facility.getFacilityCode()
                        : HpaEnrichmentImportService.SYS_HPA_REGISTRATION_NUMBER + ":" + regNum,
                "Listed on the HPA institution register (" + HpaEnrichmentImportService.BUNDLE_ID
                        + ", as at " + HpaEnrichmentImportService.SOURCE_EFFECTIVE_DATE + ")",
                actorId, tenantId, HpaEnrichmentImportService.BUNDLE_ID);

        sourceLegitimacyService.stampFromImport(facility,
                FacilityLegitimacySource.PLATFORM_OPERATIONAL,
                FacilityLegitimacyStatus.PENDING_VERIFICATION,
                false,
                HpaEnrichmentImportService.BUNDLE_ID,
                "Regulatory listing only — the regulator does not assert an operating status and "
                        + "the platform has not verified one",
                actorId, tenantId, HpaEnrichmentImportService.BUNDLE_ID);
        return true;
    }

    private String assertedRegulatoryStatus(Long facilityId) {
        try {
            return jdbc.queryForObject(ASSERTED_STATUS_SQL, String.class, facilityId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String identifierValue(Long facilityId, String system) {
        try {
            return jdbc.queryForObject(
                    "SELECT value FROM tuso.facility_identifier WHERE facility_id = ? AND system = ? LIMIT 1",
                    String.class, facilityId, system);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** How many HPA facilities still carry no legitimacy verdict. */
    public int countRemaining(UUID tenantId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM tuso.facility f WHERE f.tenant_id = ? "
                        + "AND f.regulatory_status = 'IMPORTED_PENDING_CONFIGURATION' "
                        + "AND EXISTS (SELECT 1 FROM tuso.facility_identifier i "
                        + "            WHERE i.facility_id = f.id AND i.system = 'HPA_INSTITUTION_ID') "
                        + "AND NOT EXISTS (SELECT 1 FROM tuso.facility_source_legitimacy l "
                        + "                WHERE l.facility_id = f.facility_uuid)",
                Integer.class, tenantId);
        return n == null ? 0 : n;
    }

    /** Verdict counts for verification/reporting. */
    public Map<String, Object> summary(UUID tenantId) {
        Integer hpaLegal = jdbc.queryForObject(
                "SELECT count(*) FROM tuso.facility_source_legitimacy WHERE source = 'HPA_LEGAL'", Integer.class);
        return Map.of(
                "hpaLegalRows", hpaLegal == null ? 0 : hpaLegal,
                "remainingUnstamped", countRemaining(tenantId));
    }
}
