package zw.gov.mohcc.impilo.vito.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.api.dto.ExternalClientRegistrationRequest;
import zw.gov.mohcc.impilo.vito.api.dto.ExternalClientRegistrationResponse;
import zw.gov.mohcc.impilo.vito.core.id.LegacyPhidFormat;
import zw.gov.mohcc.impilo.vito.persistence.entity.LegacyPhidImportEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.PhidPoolEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientIdentifierRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.LegacyPhidImportRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.PhidPoolRepository;
import zw.gov.mohcc.impilo.vito.service.ExternalRegistrationService;

import java.time.LocalDate;
import java.util.*;

/**
 * Bulk legacy-PHID import (Identity Contract §5.2, §14).
 *
 * <p>Reconciles a batch of MRS PHIDs into the vNext identity plane, routing each
 * record by disposition and recording full provenance:</p>
 * <ul>
 *   <li><b>Assigned</b> (a person's demographics supplied) → reuse
 *       {@link ExternalRegistrationService} to match/mint the person, then issue
 *       a {@code LEGACY_PHID} alias so the old number keeps working. The person
 *       never gets a second public identifier merely for migrating.</li>
 *   <li><b>Unassigned pool numbers</b> (preallocated, no person) → recorded in
 *       {@code vito.phid_pool} as {@code RESERVED} — never a client alias, so
 *       they cannot be accidentally issued.</li>
 *   <li><b>Conflict</b> (the same PHID already maps to a different person) →
 *       {@code QUARANTINED}; no alias is created, the steward reviews.</li>
 *   <li><b>Invalid format</b> → {@code INVALID_FORMAT}, skipped.</li>
 * </ul>
 */
@Service
public class LegacyPhidImportService {

    private static final Logger log = LoggerFactory.getLogger(LegacyPhidImportService.class);

    private final ExternalRegistrationService externalRegistration;
    private final ImpiloIdAliasService aliasService;
    private final ClientIdentifierRepository clientIdentifierRepository;
    private final PhidPoolRepository phidPoolRepository;
    private final LegacyPhidImportRepository importRepository;
    private final LegacyPhidFormat legacyPhidFormat;

    public LegacyPhidImportService(ExternalRegistrationService externalRegistration,
                                   ImpiloIdAliasService aliasService,
                                   ClientIdentifierRepository clientIdentifierRepository,
                                   PhidPoolRepository phidPoolRepository,
                                   LegacyPhidImportRepository importRepository,
                                   LegacyPhidFormat legacyPhidFormat) {
        this.externalRegistration = externalRegistration;
        this.aliasService = aliasService;
        this.clientIdentifierRepository = clientIdentifierRepository;
        this.phidPoolRepository = phidPoolRepository;
        this.importRepository = importRepository;
        this.legacyPhidFormat = legacyPhidFormat;
    }

    /** One record in an import batch. A record with no person is a pool number. */
    public record ImportRecord(
            String phid,
            String sourceFacilityId,
            LocalDate issuanceDate,
            // person demographics — null/blank for unassigned pool numbers
            String givenName,
            String familyName,
            String sex,
            LocalDate dateOfBirth,
            String nationalId) {
        boolean isAssigned() {
            return givenName != null && !givenName.isBlank()
                    && familyName != null && !familyName.isBlank();
        }
    }

    public record ImportResult(UUID batchId, int total, int assigned, int reserved,
                               int quarantined, int invalid) {}

    /**
     * Import a batch. Each record is committed in its own unit via the delegated
     * services; the provenance ledger records the outcome regardless.
     */
    @Transactional
    public ImportResult importBatch(UUID tenantId, String sourceSystem,
                                    List<ImportRecord> records, String actorId) {
        UUID batchId = UUID.randomUUID();
        int assigned = 0, reserved = 0, quarantined = 0, invalid = 0;

        for (ImportRecord rec : records) {
            String phid = rec.phid() != null ? rec.phid().trim().toUpperCase() : null;
            if (phid == null || !legacyPhidFormat.validate(phid)) {
                record(tenantId, batchId, phid, sourceSystem, rec, "INVALID_FORMAT", null,
                        "PHID failed format/check-digit validation");
                invalid++;
                continue;
            }

            // Conflict: this PHID is already attached to a person (MRS_PHID identifier).
            Optional<UUID> existingOwner = clientIdentifierRepository
                    .findByTenantIdAndIdentifierTypeAndIdentifierValue(tenantId, "MRS_PHID", phid)
                    .map(ci -> ci.getClientHealthId());

            if (rec.isAssigned()) {
                UUID healthId;
                String disposition;
                try {
                    ExternalClientRegistrationResponse resp = externalRegistration.register(
                            tenantId, toRegistrationRequest(sourceSystem, phid, rec),
                            actorId, "SYSTEM", batchId.toString());
                    healthId = resp.healthId();
                    disposition = "DEDUPLICATED".equals(resp.status()) ? "ASSIGNED_LINKED" : "ASSIGNED_NEW";
                } catch (RuntimeException e) {
                    record(tenantId, batchId, phid, sourceSystem, rec, "QUARANTINED", null,
                            "registration failed: " + e.getMessage());
                    quarantined++;
                    continue;
                }

                // Same PHID already owned by a DIFFERENT person → quarantine, no alias.
                if (existingOwner.isPresent() && !existingOwner.get().equals(healthId)) {
                    record(tenantId, batchId, phid, sourceSystem, rec, "QUARANTINED", healthId,
                            "PHID already assigned to a different person " + existingOwner.get());
                    quarantined++;
                    continue;
                }

                issueLegacyAlias(tenantId, healthId, phid);
                record(tenantId, batchId, phid, sourceSystem, rec, disposition, healthId, null);
                assigned++;
            } else {
                // Unassigned pool number — RESERVED, never a client alias.
                if (existingOwner.isPresent()) {
                    record(tenantId, batchId, phid, sourceSystem, rec, "QUARANTINED", existingOwner.get(),
                            "pool number is actually assigned to a person");
                    quarantined++;
                    continue;
                }
                reservePoolNumber(tenantId, phid, rec.sourceFacilityId());
                record(tenantId, batchId, phid, sourceSystem, rec, "RESERVED_POOL", null, null);
                reserved++;
            }
        }

        log.info("Legacy PHID import batch {} (tenant={}): assigned={} reserved={} quarantined={} invalid={}",
                batchId, tenantId, assigned, reserved, quarantined, invalid);
        return new ImportResult(batchId, records.size(), assigned, reserved, quarantined, invalid);
    }

    private void issueLegacyAlias(UUID tenantId, UUID healthId, String phid) {
        try {
            aliasService.issueAlias(tenantId, healthId, "LEGACY_PHID", phid);
        } catch (IllegalStateException alreadyActive) {
            // Idempotent re-import: an active LEGACY_PHID alias already exists.
            log.debug("LEGACY_PHID alias already active for {} — skipping", healthId);
        }
    }

    private void reservePoolNumber(UUID tenantId, String phid, String facilityId) {
        if (phidPoolRepository.findByPhid(phid).isPresent()) {
            return; // idempotent
        }
        PhidPoolEntity entry = new PhidPoolEntity();
        entry.setPhid(phid);
        entry.setFacilityId(facilityId);
        entry.setStatus("RESERVED");
        phidPoolRepository.save(entry);
    }

    private ExternalClientRegistrationRequest toRegistrationRequest(
            String sourceSystem, String phid, ImportRecord rec) {
        return new ExternalClientRegistrationRequest(
                rec.givenName(), null, rec.familyName(),
                rec.dateOfBirth(), rec.sex(),
                rec.nationalId(), null, null, null, null, null,
                sourceSystem, null, rec.sourceFacilityId(),
                null, phid);
    }

    private void record(UUID tenantId, UUID batchId, String phid, String sourceSystem,
                        ImportRecord rec, String disposition, UUID healthId, String reason) {
        LegacyPhidImportEntity e = new LegacyPhidImportEntity();
        e.setTenantId(tenantId);
        e.setBatchId(batchId);
        e.setPhid(phid != null ? phid : "");
        e.setSourceSystem(sourceSystem);
        e.setSourceFacilityId(rec.sourceFacilityId());
        e.setIssuanceDate(rec.issuanceDate());
        e.setDisposition(disposition);
        e.setHealthId(healthId);
        e.setReason(reason);
        importRepository.save(e);
    }
}
