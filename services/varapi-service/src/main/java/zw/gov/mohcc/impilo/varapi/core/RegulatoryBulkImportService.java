package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.varapi.persistence.entity.*;
import zw.gov.mohcc.impilo.varapi.persistence.repository.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regulatory bulk-import rail (ROM-U7): STAGE → MATCH → REVIEW → APPLY for a regulator's existing
 * data — registered/licensed people and their conditions of practice, and council personnel.
 *
 * <p>Load-bearing invariants (mirroring the HPA importer):</p>
 * <ul>
 *   <li><b>Staging is immutable</b> — the uploaded rows are kept verbatim.</li>
 *   <li><b>Match against live truth</b> — REGISTRANT rows match an existing council registration by
 *       registration number → MATCHED_EXISTING, else NEW; missing required fields → INVALID.</li>
 *   <li><b>Apply is review-gated + idempotent</b> — only ACCEPTED, non-INVALID rows apply; an
 *       already-APPLIED row is skipped on re-run.</li>
 *   <li><b>Grants no authority</b> — apply writes register data (a council registration record)
 *       only; it never creates a login, an appointment, or work access. A row with no resolvable
 *       provider is SKIPPED for onboarding, never silently granted.</li>
 * </ul>
 */
@Service
public class RegulatoryBulkImportService {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryBulkImportService.class);

    private final RegulatoryImportBatchRepository batchRepository;
    private final RegulatoryImportRowRepository rowRepository;
    private final ProviderCouncilRegistrationRecordRepository registrationRepository;
    private final CouncilRepository councilRepository;
    private final ProviderRepository providerRepository;
    private final ObjectMapper objectMapper;

    public RegulatoryBulkImportService(RegulatoryImportBatchRepository batchRepository,
                                       RegulatoryImportRowRepository rowRepository,
                                       ProviderCouncilRegistrationRecordRepository registrationRepository,
                                       CouncilRepository councilRepository,
                                       ProviderRepository providerRepository,
                                       ObjectMapper objectMapper) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.registrationRepository = registrationRepository;
        this.councilRepository = councilRepository;
        this.providerRepository = providerRepository;
        this.objectMapper = objectMapper;
    }

    /** STAGE + MATCH: persist the batch + rows and classify each row against live truth. */
    @Transactional
    public RegulatoryImportBatchEntity stage(UUID tenantId, Long councilId, String recordType,
                                             String sourceLabel, String filename, String uploadedBy,
                                             List<Map<String, Object>> rows) {
        RegulatoryImportBatchEntity batch = new RegulatoryImportBatchEntity();
        batch.setTenantId(tenantId);
        batch.setCouncilId(councilId);
        batch.setRecordType(recordType == null ? "REGISTRANT" : recordType);
        batch.setSourceLabel(sourceLabel);
        batch.setFilename(filename);
        batch.setUploadedBy(uploadedBy);
        batch.setTotalRows(rows == null ? 0 : rows.size());
        batch.setStatus("STAGED");
        batch = batchRepository.save(batch);

        int i = 0;
        for (Map<String, Object> raw : (rows == null ? List.<Map<String, Object>>of() : rows)) {
            RegulatoryImportRowEntity row = new RegulatoryImportRowEntity();
            row.setTenantId(tenantId);
            row.setBatchId(batch.getId());
            row.setRowIndex(i++);
            row.setRaw(writeJson(raw));
            classify(tenantId, batch.getRecordType(), councilId, raw, row);
            rowRepository.save(row);
        }
        return batch;
    }

    private void classify(UUID tenantId, String recordType, Long councilId, Map<String, Object> raw,
                          RegulatoryImportRowEntity row) {
        if (!"REGISTRANT".equals(recordType)) {
            // APPOINTMENT/LICENCE land in review only for now — they materialise via their owning
            // lanes (org-registry appointments / licence records), never here.
            row.setMatchStatus("NEW");
            row.setMessage("Staged for review (" + recordType + ").");
            return;
        }
        String regNo = str(raw, "registrationNumber");
        if (regNo == null || councilId == null) {
            row.setMatchStatus("INVALID");
            row.setMessage("Missing registrationNumber or council.");
            return;
        }
        var existing = registrationRepository
                .findFirstByTenantIdAndCouncil_IdAndRegistrationNumberIgnoreCase(tenantId, councilId, regNo);
        if (existing.isPresent()) {
            row.setMatchStatus("MATCHED_EXISTING");
            row.setMatchedRef(String.valueOf(existing.get().getId()));
            row.setMessage("Matches an existing council registration.");
        } else {
            row.setMatchStatus("NEW");
            row.setMessage("New registration (no existing match).");
        }
    }

    /** A steward accepts/rejects a staged row. */
    @Transactional
    public RegulatoryImportRowEntity review(UUID tenantId, Long rowId, boolean accept) {
        RegulatoryImportRowEntity row = rowRepository.findById(rowId)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Import row not found: " + rowId));
        row.setReviewStatus(accept ? "ACCEPTED" : "REJECTED");
        return rowRepository.save(row);
    }

    /**
     * APPLY: materialise ACCEPTED, non-INVALID REGISTRANT rows as council registration records —
     * grants no authority. Idempotent (already-APPLIED rows are skipped). Rows whose person cannot
     * be resolved to a provider are SKIPPED for onboarding.
     */
    @Transactional
    public RegulatoryImportBatchEntity apply(UUID tenantId, Long batchId) {
        RegulatoryImportBatchEntity batch = batchRepository.findById(batchId)
                .filter(b -> tenantId.equals(b.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Import batch not found: " + batchId));
        CouncilEntity council = batch.getCouncilId() == null ? null
                : councilRepository.findById(batch.getCouncilId()).orElse(null);

        int applied = 0;
        for (RegulatoryImportRowEntity row : rowRepository.findByBatchIdOrderByRowIndexAsc(batchId)) {
            if (!"ACCEPTED".equals(row.getReviewStatus()) || "INVALID".equals(row.getMatchStatus())
                    || "APPLIED".equals(row.getApplyStatus())) {
                continue;
            }
            if (!"REGISTRANT".equals(batch.getRecordType()) || council == null) {
                row.setApplyStatus("SKIPPED");
                row.setMessage("Not applied here — routed to its owning lane for onboarding.");
                rowRepository.save(row);
                continue;
            }
            Map<String, Object> raw = readJson(row.getRaw());
            ProviderEntity provider = resolveProvider(tenantId, raw);
            if (provider == null) {
                row.setApplyStatus("SKIPPED");
                row.setMessage("No matching provider — staged for onboarding (grants no authority).");
                rowRepository.save(row);
                continue;
            }
            if ("MATCHED_EXISTING".equals(row.getMatchStatus()) && row.getMatchedRef() != null) {
                row.setApplyStatus("APPLIED"); // already on the register; idempotent no-op enrichment
            } else {
                ProviderCouncilRegistrationRecordEntity rec = new ProviderCouncilRegistrationRecordEntity();
                rec.setTenantId(tenantId);
                rec.setProvider(provider);
                rec.setCouncil(council);
                rec.setRegistrationNumber(str(raw, "registrationNumber"));
                rec.setRegistrationCategory(str(raw, "registrationCategory"));
                rec.setStatus(str(raw, "status") != null ? str(raw, "status") : "REGISTERED");
                registrationRepository.save(rec);
                row.setApplyStatus("APPLIED");
            }
            row.setMessage("Applied to the register (grants no authority).");
            rowRepository.save(row);
            applied++;
        }
        batch.setStatus("APPLIED");
        batch.setAppliedRows(batch.getAppliedRows() + applied);
        return batchRepository.save(batch);
    }

    private ProviderEntity resolveProvider(UUID tenantId, Map<String, Object> raw) {
        String healthId = str(raw, "healthId");
        if (healthId != null) {
            try {
                return providerRepository.findByTenantIdAndImpiloHealthId(tenantId, UUID.fromString(healthId)).orElse(null);
            } catch (IllegalArgumentException ignored) { /* fall through */ }
        }
        String providerPublicId = str(raw, "providerPublicId");
        if (providerPublicId != null) {
            return providerRepository.findByProviderPublicIdAndTenantId(providerPublicId, tenantId).orElse(null);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<RegulatoryImportBatchEntity> batches(UUID tenantId) {
        return batchRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<RegulatoryImportRowEntity> rows(Long batchId) {
        return rowRepository.findByBatchIdOrderByRowIndexAsc(batchId);
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o == null ? Map.of() : o); }
        catch (Exception e) { return "{}"; }
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String s) {
        try { return objectMapper.readValue(s, Map.class); } catch (Exception e) { return Map.of(); }
    }
    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return (v == null || v.toString().isBlank()) ? null : v.toString();
    }
}
