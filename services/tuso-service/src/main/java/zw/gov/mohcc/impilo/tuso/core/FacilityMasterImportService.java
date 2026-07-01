package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityImportRunDtos;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityMasterImportDtos;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityContactEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGeoEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierSystem;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityImportRowEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityImportRunEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityContactRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGeoRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityImportRowRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityImportRunRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class FacilityMasterImportService {

    /** @see FacilityIdentifierSystem#MASTER_FACILITY_UID — import correlation key, not the public code. */
    public static final String MASTER_FACILITY_UID_SYSTEM = FacilityIdentifierSystem.MASTER_FACILITY_UID;
    public static final String PACK_ID = "master-health-facility-2024-07-23";
    /** Canonical source-provenance label for this national master dataset. */
    public static final String SOURCE_LABEL = "MASTER_HEALTH_FACILITY_2024_07_23";
    /** Source dataset date carried on records loaded from the 2024-07-23 clean CSV. */
    public static final String SOURCE_LABEL_DATE = "2024-07-23";

    private static final Logger log = LoggerFactory.getLogger(FacilityMasterImportService.class);

    private final FacilityRepository facilityRepository;
    private final FacilityIdentifierRepository identifierRepository;
    private final FacilityContactRepository contactRepository;
    private final FacilityGeoRepository geoRepository;
    private final FacilityImportRunRepository importRunRepository;
    private final FacilityImportRowRepository importRowRepository;
    private final ObjectMapper objectMapper;

    public FacilityMasterImportService(
            FacilityRepository facilityRepository,
            FacilityIdentifierRepository identifierRepository,
            FacilityContactRepository contactRepository,
            FacilityGeoRepository geoRepository,
            FacilityImportRunRepository importRunRepository,
            FacilityImportRowRepository importRowRepository,
            ObjectMapper objectMapper) {
        this.facilityRepository = facilityRepository;
        this.identifierRepository = identifierRepository;
        this.contactRepository = contactRepository;
        this.geoRepository = geoRepository;
        this.importRunRepository = importRunRepository;
        this.importRowRepository = importRowRepository;
        this.objectMapper = objectMapper;
    }

    public List<FacilityMasterImportDtos.MasterFacilitySeedRecord> loadPackFromRepo(Path packJson) throws IOException {
        List<Map<String, Object>> raw = objectMapper.readValue(
                Files.readString(packJson),
                new TypeReference<List<Map<String, Object>>>() {});
        List<FacilityMasterImportDtos.MasterFacilitySeedRecord> records = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            records.add(new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                    str(row, "facility_uid"),
                    str(row, "facility_code"),
                    str(row, "facility_name"),
                    str(row, "province"),
                    str(row, "district"),
                    str(row, "facility_type"),
                    str(row, "ownership"),
                    str(row, "location_context"),
                    str(row, "service_level"),
                    str(row, "status"),
                    intOrNull(row.get("bed_capacity")),
                    doubleOrNull(row.get("latitude")),
                    doubleOrNull(row.get("longitude")),
                    str(row, "contact_phone_e164"),
                    str(row, "source_dataset_date")));
        }
        return records;
    }

    /**
     * Load the canonicalised master pack CSV ({@code clean_tuso_facility_import.csv}) into seed records.
     *
     * <p>The clean CSV carries {@code *_canonical} columns, completeness flags and a {@code source_row}
     * index — but <b>no</b> {@code facility_uid}. Product truth forbids using the public facility code as
     * the correlation key, so a stable, code-independent {@code MASTER_FACILITY_UID} is derived from the
     * dataset provenance: {@code MHF-<sourceLabel>-row<source_row>}. That handle is deterministic across
     * re-imports (same row ⇒ same handle ⇒ same internal facility, never regenerated).</p>
     *
     * <p>Only the clean, import-eligible dataset should be passed here — the excluded/review CSVs
     * (missing-code, duplicate-code, duplicate-name) are never auto-imported.</p>
     */
    public List<FacilityMasterImportDtos.MasterFacilitySeedRecord> loadPackFromCsv(Path packCsv) throws IOException {
        List<String> lines = Files.readAllLines(packCsv);
        List<FacilityMasterImportDtos.MasterFacilitySeedRecord> records = new ArrayList<>();
        if (lines.isEmpty()) {
            return records;
        }
        // Strip a UTF-8 BOM if present on the header line.
        String header = lines.get(0);
        if (!header.isEmpty() && header.charAt(0) == '﻿') {
            header = header.substring(1);
        }
        List<String> cols = parseCsvLine(header);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < cols.size(); i++) {
            idx.put(cols.get(i).trim(), i);
        }
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> f = parseCsvLine(lines.get(i));
            String sourceRow = cell(f, idx, "source_row");
            String correlationUid = "MHF-" + SOURCE_LABEL + "-row" + (isBlank(sourceRow) ? String.valueOf(i) : sourceRow.trim());
            // Preserve the raw source columns verbatim so canonicalisation never loses them.
            Map<String, Object> raw = new LinkedHashMap<>();
            putIfPresent(raw, "source_row", cell(f, idx, "source_row"));
            putIfPresent(raw, "province", cell(f, idx, "province"));
            putIfPresent(raw, "district", cell(f, idx, "district"));
            putIfPresent(raw, "facility_name", cell(f, idx, "facility_name"));
            putIfPresent(raw, "facility_code", cell(f, idx, "facility_code"));
            putIfPresent(raw, "latitude", cell(f, idx, "latitude"));
            putIfPresent(raw, "longitude", cell(f, idx, "longitude"));
            putIfPresent(raw, "facility_type_raw", cell(f, idx, "facility_type_raw"));
            putIfPresent(raw, "ownership_raw", cell(f, idx, "ownership_raw"));
            putIfPresent(raw, "location_raw", cell(f, idx, "location_raw"));
            putIfPresent(raw, "level_raw", cell(f, idx, "level_raw"));
            putIfPresent(raw, "contact_raw", cell(f, idx, "contact_raw"));
            putIfPresent(raw, "status_raw", cell(f, idx, "status_raw"));
            putIfPresent(raw, "bed_capacity", cell(f, idx, "bed_capacity"));
            putIfPresent(raw, "date_opened", cell(f, idx, "date_opened"));
            putIfPresent(raw, "date_closed", cell(f, idx, "date_closed"));
            putIfPresent(raw, "comments", cell(f, idx, "comments"));
            records.add(new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                    correlationUid,
                    cell(f, idx, "facility_code"),
                    cell(f, idx, "facility_name"),
                    cell(f, idx, "province"),
                    cell(f, idx, "district"),
                    // Prefer canonical classifications; the raw columns are preserved in rawValues.
                    firstNonBlank(cell(f, idx, "facility_type_canonical"), cell(f, idx, "facility_type_raw")),
                    firstNonBlank(cell(f, idx, "ownership_canonical"), cell(f, idx, "ownership_raw")),
                    firstNonBlank(cell(f, idx, "location_canonical"), cell(f, idx, "location_raw")),
                    firstNonBlank(cell(f, idx, "level_canonical"), cell(f, idx, "level_raw")),
                    // Status: pass the raw ("Open"/blank) so open-detection and MISSING_REQUIRES_CONFIRMATION
                    // behave correctly; a blank raw status is preserved as missing, never faked to ACTIVE.
                    cell(f, idx, "status_raw"),
                    intOrNull(cell(f, idx, "bed_capacity")),
                    doubleOrNull(cell(f, idx, "latitude")),
                    doubleOrNull(cell(f, idx, "longitude")),
                    cell(f, idx, "contact_raw"),
                    SOURCE_LABEL_DATE,
                    raw));
        }
        return records;
    }

    @Transactional
    public FacilityMasterImportDtos.FacilityMasterImportResponse importPack(
            FacilityMasterImportDtos.FacilityMasterImportRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        Instant startedAt = Instant.now();
        List<FacilityMasterImportDtos.FacilityMasterImportRowResult> results = new ArrayList<>();
        List<RowDraft> rowDrafts = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        int warnings = 0;

        // Product truth: facility names appearing more than once within this batch are duplicates that
        // require human review — never auto-imported/merged. (The clean pack already excludes these; this
        // is an in-service guard so any batch is safe regardless of how it was assembled.)
        Set<String> duplicateNames = duplicateNamesInBatch(request.records());

        for (FacilityMasterImportDtos.MasterFacilitySeedRecord record : request.records()) {
            try {
                ImportDecision decision =
                        evaluateRecord(tenantId, record, request.reconcileDuplicateCodes(), duplicateNames);
                if (decision.warning() != null) {
                    warnings++;
                }
                if (request.dryRun()) {
                    results.add(rowResult(record, decision.dryRunOutcome(), decision.warning(), decision.message(), decision.facilityId()));
                    switch (decision.dryRunOutcome()) {
                        case "WOULD_CREATE" -> {
                            created++;
                            rowDrafts.add(readyDraft(record, decision, "CREATE"));
                        }
                        case "WOULD_UPDATE" -> {
                            updated++;
                            rowDrafts.add(readyDraft(record, decision, "UPDATE"));
                        }
                        case "SKIPPED" -> {
                            skipped++;
                            rowDrafts.add(exclusionDraft(record, decision));
                        }
                        default -> { }
                    }
                    continue;
                }
                if ("SKIPPED".equals(decision.dryRunOutcome())) {
                    skipped++;
                    results.add(rowResult(record, "SKIPPED", decision.warning(), decision.message(), decision.facilityId()));
                    rowDrafts.add(exclusionDraft(record, decision));
                    continue;
                }

                FacilityEntity facility = decision.existing().orElseGet(FacilityEntity::new);
                boolean isNew = facility.getId() == null;
                Long matchedId = isNew ? null : facility.getId();
                applyRecord(tenantId, actorId, facility, record, decision.resolvedCode());
                facility = facilityRepository.save(facility);
                // Internal correlation key: matches the same facility on re-import (never regenerates
                // the internal facility id / facility_uuid).
                upsertIdentifier(facility, FacilityIdentifierSystem.MASTER_FACILITY_UID, record.facilityUid());
                // Public administrative code recorded as an EXTERNAL identifier — for matching, reporting
                // and interoperability only. It is not the internal identity and never an authority.
                upsertIdentifier(facility, FacilityIdentifierSystem.NATIONAL_FACILITY_CODE, decision.resolvedCode());
                // Provenance handle for the source row (audit/traceability), distinct from the code.
                upsertIdentifier(facility, FacilityIdentifierSystem.IMPORT_SOURCE_ROW_ID, record.facilityUid());
                upsertContact(facility, record.contactPhoneE164());
                upsertGeo(facility, record);

                if (isNew) {
                    created++;
                    results.add(rowResult(record, "CREATED", decision.warning(), null, facility.getId()));
                    rowDrafts.add(importedDraft(record, "CREATE", null, facility.getId()));
                } else {
                    updated++;
                    results.add(rowResult(record, "UPDATED", decision.warning(), null, facility.getId()));
                    rowDrafts.add(importedDraft(record, "UPDATE", matchedId, facility.getId()));
                }
            } catch (Exception e) {
                failed++;
                warnings++;
                results.add(rowResult(record, "FAILED", "DOWNSTREAM", e.getMessage(), null));
                rowDrafts.add(failedDraft(record, e.getMessage()));
                log.warn("Master pack import failed for {}: {}", record.facilityUid(), e.getMessage());
            }
        }

        Map<String, Object> qualitySummary = buildQualitySummary(request.records(), results, warnings);

        // Persist an audit row for the batch (dry-run or real) so operators can review import history,
        // then persist the row-level outcomes linked to it.
        Long runId = persistImportRun(tenantId, actorId, request.dryRun(), request.records().size(),
                created, updated, skipped, failed, warnings, startedAt, qualitySummary);
        persistImportRows(runId, rowDrafts);

        return new FacilityMasterImportDtos.FacilityMasterImportResponse(
                request.dryRun(),
                request.records().size(),
                created,
                updated,
                skipped,
                failed,
                warnings,
                results,
                qualitySummary);
    }

    private Long persistImportRun(
            UUID tenantId, String actorId, boolean dryRun, int total,
            int created, int updated, int skipped, int failed, int warnings,
            Instant startedAt, Map<String, Object> qualitySummary) {
        try {
            FacilityImportRunEntity run = new FacilityImportRunEntity();
            run.setTenantId(tenantId);
            run.setPackId(PACK_ID);
            run.setDryRun(dryRun);
            run.setRecordsTotal(total);
            run.setRecordsCreated(created);
            run.setRecordsUpdated(updated);
            run.setRecordsSkipped(skipped);
            run.setRecordsFailed(failed);
            run.setWarningsCount(warnings);
            run.setStatus(failed > 0 ? "COMPLETED_WITH_FAILURES" : "COMPLETED");
            run.setQualityReport(qualitySummary);
            run.setStartedAt(startedAt);
            run.setCompletedAt(Instant.now());
            run.setInitiatedBy(actorId);
            return importRunRepository.save(run).getId();
        } catch (Exception e) {
            // Audit persistence must never fail the import itself; log and continue.
            log.warn("Failed to persist facility_import_run audit row: {}", e.getMessage());
            return null;
        }
    }

    /** Persist the row-level outcomes for a run (best-effort; never fails the import). */
    private void persistImportRows(Long runId, List<RowDraft> drafts) {
        if (runId == null || drafts.isEmpty()) {
            return;
        }
        try {
            List<FacilityImportRowEntity> entities = new ArrayList<>(drafts.size());
            for (RowDraft d : drafts) {
                entities.add(toRowEntity(runId, d));
            }
            importRowRepository.saveAll(entities);
        } catch (Exception e) {
            log.warn("Failed to persist facility_import_row rows for run {}: {}", runId, e.getMessage());
        }
    }

    private FacilityImportRowEntity toRowEntity(Long runId, RowDraft d) {
        FacilityMasterImportDtos.MasterFacilitySeedRecord r = d.record();
        FacilityImportRowEntity e = new FacilityImportRowEntity();
        e.setImportRunId(runId);
        e.setSourceLabel(SOURCE_LABEL);
        e.setSourceRow(extractSourceRow(r));
        e.setCorrelationKey(r.facilityUid());
        e.setProvince(r.province());
        e.setDistrict(r.district());
        e.setFacilityName(r.facilityName());
        e.setFacilityCode(r.facilityCode());
        e.setLatitude(r.latitude());
        e.setLongitude(r.longitude());
        e.setFacilityType(r.facilityType());
        e.setOwnership(r.ownership());
        e.setLocationCategory(r.locationContext());
        e.setFacilityLevel(r.serviceLevel());
        e.setContact(r.contactPhoneE164());
        e.setOperatingStatus(r.status());
        e.setBedCapacity(r.bedCapacity());
        e.setRawValues(r.rawValues());
        e.setOutcome(d.outcome());
        e.setImportDecision(d.decision());
        e.setExclusionReason(d.exclusionReason());
        e.setDuplicateType(d.duplicateType());
        e.setDuplicateGroupKey(d.duplicateGroupKey());
        e.setMatchedFacilityId(d.matchedFacilityId());
        e.setResultFacilityId(d.resultFacilityId());
        e.setValidationErrors(d.validationErrors());

        Map<String, Object> missing = acceptableMissingFlags(r);
        boolean hasMissing = missing.values().stream().anyMatch(v -> Boolean.TRUE.equals(v));
        e.setAcceptableMissing(missing);
        e.setHasAcceptableMissing(hasMissing);
        return e;
    }

    private static String extractSourceRow(FacilityMasterImportDtos.MasterFacilitySeedRecord r) {
        if (r.rawValues() != null && r.rawValues().get("source_row") != null) {
            return String.valueOf(r.rawValues().get("source_row"));
        }
        String uid = r.facilityUid();
        int i = uid == null ? -1 : uid.lastIndexOf("row");
        return (i >= 0 && i + 3 < uid.length()) ? uid.substring(i + 3) : null;
    }

    /** Structured acceptable-missing flags per row — surfaced so the frontend can show exact gaps. */
    private static Map<String, Object> acceptableMissingFlags(
            FacilityMasterImportDtos.MasterFacilitySeedRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("missing_latitude", r.latitude() == null);
        m.put("missing_longitude", r.longitude() == null);
        m.put("missing_facility_type", isBlank(r.facilityType()));
        m.put("missing_ownership", isBlank(r.ownership()));
        m.put("missing_operating_status", isBlank(r.status()));
        return m;
    }

    private static RowDraft readyDraft(
            FacilityMasterImportDtos.MasterFacilitySeedRecord record, ImportDecision d, String decision) {
        return new RowDraft(record, FacilityImportRowEntity.READY_FOR_IMPORT, decision, null,
                null, null, d.facilityId(), null, null);
    }

    private static RowDraft importedDraft(
            FacilityMasterImportDtos.MasterFacilitySeedRecord record, String decision,
            Long matchedId, Long resultId) {
        return new RowDraft(record, FacilityImportRowEntity.IMPORTED, decision, null,
                null, null, matchedId, resultId, null);
    }

    private static RowDraft failedDraft(
            FacilityMasterImportDtos.MasterFacilitySeedRecord record, String error) {
        return new RowDraft(record, FacilityImportRowEntity.FAILED, "FAIL", null,
                null, null, null, null, error);
    }

    private static RowDraft exclusionDraft(
            FacilityMasterImportDtos.MasterFacilitySeedRecord record, ImportDecision d) {
        String warning = d.warning();
        String outcome;
        String dupType = null;
        String dupKey = null;
        if ("EXCLUDED_MISSING_FACILITY_CODE".equals(warning)) {
            outcome = FacilityImportRowEntity.EXCLUDED_MISSING_FACILITY_CODE;
        } else if ("EXCLUDED_DUPLICATE_FACILITY_CODE".equals(warning)) {
            outcome = FacilityImportRowEntity.EXCLUDED_DUPLICATE_FACILITY_CODE;
            dupType = FacilityImportRowEntity.DUP_TYPE_CODE;
            dupKey = "CODE:" + (d.resolvedCode() != null ? d.resolvedCode() : record.facilityCode());
        } else if ("EXCLUDED_DUPLICATE_FACILITY_NAME".equals(warning)) {
            outcome = FacilityImportRowEntity.EXCLUDED_DUPLICATE_FACILITY_NAME;
            dupType = FacilityImportRowEntity.DUP_TYPE_NAME;
            dupKey = "NAME:" + normaliseName(record.facilityName());
        } else {
            outcome = FacilityImportRowEntity.REQUIRES_REVIEW;
        }
        return new RowDraft(record, outcome, "EXCLUDE", d.message(), dupType, dupKey,
                d.facilityId(), null, null);
    }

    private record RowDraft(
            FacilityMasterImportDtos.MasterFacilitySeedRecord record,
            String outcome,
            String decision,
            String exclusionReason,
            String duplicateType,
            String duplicateGroupKey,
            Long matchedFacilityId,
            Long resultFacilityId,
            String validationErrors) {}

    /** List import runs for the current tenant, most recent first. */
    @Transactional(readOnly = true)
    public List<FacilityImportRunDtos.FacilityImportRunView> listRuns() {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return importRunRepository.findByTenantIdOrderByStartedAtDesc(tenantId).stream()
                .map(FacilityMasterImportService::toRunView)
                .toList();
    }

    /** Read a single import run by id, scoped to the current tenant. */
    @Transactional(readOnly = true)
    public Optional<FacilityImportRunDtos.FacilityImportRunView> getRun(Long runId) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return importRunRepository.findById(runId)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .map(FacilityMasterImportService::toRunView);
    }

    private static FacilityImportRunDtos.FacilityImportRunView toRunView(FacilityImportRunEntity run) {
        Map<String, Object> q = run.getQualityReport() != null ? run.getQualityReport() : Map.of();
        FacilityImportRunDtos.RunTotals totals = new FacilityImportRunDtos.RunTotals(
                run.getRecordsTotal(),
                asLong(q.get("records_imported"), run.getRecordsCreated() + run.getRecordsUpdated()),
                run.getRecordsCreated(),
                run.getRecordsUpdated(),
                run.getRecordsSkipped(),
                run.getRecordsFailed(),
                run.getWarningsCount(),
                asLong(q.get("records_missing_facility_code"), 0),
                asLong(q.get("records_duplicate_facility_code"), 0),
                asLong(q.get("records_duplicate_facility_name"), 0),
                asLong(q.get("records_excluded_total"), run.getRecordsSkipped()),
                asLong(q.get("records_acceptable_missing"), 0),
                asLong(q.get("records_with_valid_coordinates"), 0));
        return new FacilityImportRunDtos.FacilityImportRunView(
                run.getId(),
                run.getPackId(),
                asString(q.get("source_label"), SOURCE_LABEL),
                null, // source file name not captured per-run yet — documented as next backend slice
                run.getStatus(),
                run.isDryRun(),
                run.getInitiatedBy(),
                run.getStartedAt(),
                run.getCompletedAt(),
                totals,
                run.getQualityReport());
    }

    private static long asLong(Object v, long fallback) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String asString(Object v, String fallback) {
        return v == null ? fallback : String.valueOf(v);
    }

    /** Normalised facility names that appear more than once in the batch (case/space-insensitive). */
    private static Set<String> duplicateNamesInBatch(
            List<FacilityMasterImportDtos.MasterFacilitySeedRecord> records) {
        Map<String, Integer> counts = new HashMap<>();
        for (FacilityMasterImportDtos.MasterFacilitySeedRecord r : records) {
            String key = normaliseName(r.facilityName());
            if (key != null) {
                counts.merge(key, 1, Integer::sum);
            }
        }
        Set<String> dups = new java.util.HashSet<>();
        counts.forEach((k, v) -> {
            if (v > 1) {
                dups.add(k);
            }
        });
        return dups;
    }

    private static String normaliseName(String name) {
        if (isBlank(name)) {
            return null;
        }
        return name.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private ImportDecision evaluateRecord(
            UUID tenantId,
            FacilityMasterImportDtos.MasterFacilitySeedRecord record,
            boolean reconcileDuplicateCodes,
            Set<String> duplicateNames) {
        Optional<FacilityEntity> byUid = findByMasterUid(record.facilityUid());
        String resolvedCode = resolveFacilityCode(record);
        boolean missingCode = resolvedCode == null;
        String warning = hasValidCoordinates(record) ? null : "MISSING_COORDINATES";
        String message = null;

        if (byUid.isPresent()) {
            // Already absorbed previously — refresh from master (blanks never overwrite verified data).
            return new ImportDecision(byUid, "WOULD_UPDATE", warning, message, resolvedCode, byUid.get().getId());
        }

        // Product truth: a row with no facility code is EXCLUDED from import; no synthetic code is made.
        if (missingCode) {
            return new ImportDecision(Optional.empty(), "SKIPPED", "EXCLUDED_MISSING_FACILITY_CODE",
                    "Excluded: missing facility code (not imported; no synthetic code generated)", null, null);
        }

        // Product truth: a duplicate facility name within the batch is excluded for human review — never
        // auto-imported, never auto-merged (two distinct facilities may legitimately share a name).
        if (duplicateNames.contains(normaliseName(record.facilityName()))) {
            return new ImportDecision(Optional.empty(), "SKIPPED", "EXCLUDED_DUPLICATE_FACILITY_NAME",
                    "Excluded for review: facility name duplicated within the batch", resolvedCode, null);
        }

        Optional<FacilityEntity> byCode = facilityRepository.findByTenantIdAndFacilityCode(tenantId, record.facilityCode());
        if (byCode.isPresent()) {
            if (reconcileDuplicateCodes) {
                return new ImportDecision(Optional.of(byCode.get()), "WOULD_UPDATE", "DUPLICATE_FACILITY_CODE",
                        "Reconciling existing facility by code", resolvedCode, byCode.get().getId());
            }
            // Product truth: duplicate facility code is excluded for human review, not auto-imported/merged.
            return new ImportDecision(Optional.empty(), "SKIPPED", "EXCLUDED_DUPLICATE_FACILITY_CODE",
                    "Excluded for review: facility code already assigned to facility " + byCode.get().getId(),
                    resolvedCode, byCode.get().getId());
        }

        return new ImportDecision(Optional.empty(), "WOULD_CREATE", warning, message, resolvedCode, null);
    }

    private void applyRecord(
            UUID tenantId,
            String actorId,
            FacilityEntity facility,
            FacilityMasterImportDtos.MasterFacilitySeedRecord record,
            String resolvedCode) {
        boolean isNew = facility.getId() == null;
        if (isNew) {
            facility.setTenantId(tenantId);
            facility.setFacilityCode(resolvedCode);
            facility.setVersion(1);
            facility.setCreatedBy(actorId);
            // Product truth: imported facilities are NOT operational — they enter pending configuration.
            facility.setRegulatoryStatus(FacilityRegulatoryStatus.IMPORTED_PENDING_CONFIGURATION);
            facility.setRegulatoryStatusUpdatedAt(Instant.now());
        } else {
            facility.setVersion(facility.getVersion() + 1);
        }

        // Master-list values win only when PRESENT; blank CSV values never overwrite existing verified data.
        setIfPresent(record.facilityName(), facility::setName);
        setIfPresent(record.facilityType(), facility::setFacilityType);
        setIfPresent(record.province(), facility::setProvince);
        setIfPresent(record.district(), facility::setDistrict);
        setIfPresent(record.ownership(), facility::setOwnership);
        setIfPresent(record.serviceLevel(), facility::setLevel);
        facility.setDescription("Master Health Facility List import (" + PACK_ID + ")");

        if (hasValidCoordinates(record)) {
            facility.setLatitude(BigDecimal.valueOf(record.latitude()));
            facility.setLongitude(BigDecimal.valueOf(record.longitude()));
        }

        // Operating status: NEVER assume ACTIVE from a blank. A blank status requires confirmation and
        // must not overwrite a previously verified status.
        if (!isBlank(record.status())) {
            boolean open = "open".equalsIgnoreCase(record.status().trim());
            facility.setStatus(open ? "ACTIVE" : "INACTIVE");
            facility.setOperationalStatus(open ? "OPERATIONAL" : "NON_OPERATIONAL");
        } else if (isNew) {
            facility.setOperationalStatus("MISSING_REQUIRES_CONFIRMATION");
        }
        facility.setUpdatedBy(actorId);

        Map<String, Object> metadata = facility.getMetadata() != null
                ? new HashMap<>(facility.getMetadata())
                : new LinkedHashMap<>();
        metadata.put("facility_uid", record.facilityUid());
        metadata.put("source_dataset_date", record.sourceDatasetDate());
        metadata.put("location_context", record.locationContext());
        metadata.put("bed_capacity", record.bedCapacity());
        metadata.put("import_pack_id", PACK_ID);
        metadata.put("source_label", SOURCE_LABEL);
        metadata.put("has_valid_coordinates", hasValidCoordinates(record));
        // Structured completeness flags so the frontend can show missing acceptable fields — nothing faked.
        metadata.put("geospatial_incomplete", !hasValidCoordinates(record));
        metadata.put("missing_facility_type", isBlank(record.facilityType()));
        metadata.put("missing_ownership", isBlank(record.ownership()));
        metadata.put("missing_operating_status", isBlank(record.status()));
        if (isBlank(record.facilityCode())) {
            metadata.put("missing_facility_code", true);
        }
        facility.setMetadata(metadata);
    }

    /**
     * Idempotently record an external identifier ({@code system}/{@code value}) for a facility. Blank
     * values are skipped (e.g. a facility with no national code never gets an empty NATIONAL_FACILITY_CODE
     * row). These identifiers are external, public matching handles — never internal identity or authority.
     */
    private void upsertIdentifier(FacilityEntity facility, String system, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        Optional<FacilityIdentifierEntity> existing =
                identifierRepository.findBySystemAndValue(system, value.trim());
        if (existing.isPresent()) {
            return;
        }
        FacilityIdentifierEntity ident = new FacilityIdentifierEntity();
        ident.setFacility(facility);
        ident.setSystem(system);
        ident.setValue(value.trim());
        ident.setActive(true);
        identifierRepository.save(ident);
    }

    private void upsertContact(FacilityEntity facility, String phone) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        List<FacilityContactEntity> contacts = contactRepository.findByFacilityId(facility.getId());
        boolean exists = contacts.stream().anyMatch(c -> phone.equals(c.getPhone()));
        if (exists) {
            return;
        }
        FacilityContactEntity contact = new FacilityContactEntity();
        contact.setFacility(facility);
        contact.setContactType("OPERATIONAL");
        contact.setPhone(phone);
        contact.setRole("FACILITY_CONTACT");
        contactRepository.save(contact);
    }

    private void upsertGeo(FacilityEntity facility, FacilityMasterImportDtos.MasterFacilitySeedRecord record) {
        if (!hasValidCoordinates(record)) {
            return;
        }
        FacilityGeoEntity geo = geoRepository.findByFacilityId(facility.getId()).orElseGet(FacilityGeoEntity::new);
        geo.setFacility(facility);
        geo.setProvince(record.province());
        geo.setDistrict(record.district());
        if (record.locationContext() != null && !record.locationContext().isBlank()) {
            geo.setAddressLine1(record.locationContext());
        }
        geoRepository.save(geo);
    }

    private Optional<FacilityEntity> findByMasterUid(String facilityUid) {
        return identifierRepository.findBySystemAndValue(MASTER_FACILITY_UID_SYSTEM, facilityUid)
                .map(FacilityIdentifierEntity::getFacility);
    }

    private static String resolveFacilityCode(FacilityMasterImportDtos.MasterFacilitySeedRecord record) {
        if (record.facilityCode() != null && !record.facilityCode().isBlank()) {
            return record.facilityCode().trim();
        }
        // Product truth: NEVER synthesise a facility code. A missing-code row is excluded from import.
        return null;
    }

    private static void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasValidCoordinates(FacilityMasterImportDtos.MasterFacilitySeedRecord record) {
        if (record.latitude() == null || record.longitude() == null) {
            return false;
        }
        double lat = record.latitude();
        double lng = record.longitude();
        if (lat == 0 && lng == 0) {
            return false;
        }
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    private static FacilityMasterImportDtos.FacilityMasterImportRowResult rowResult(
            FacilityMasterImportDtos.MasterFacilitySeedRecord record,
            String outcome,
            String qualityFlag,
            String message,
            Long facilityId) {
        return new FacilityMasterImportDtos.FacilityMasterImportRowResult(
                record.facilityUid(),
                record.facilityCode(),
                record.facilityName(),
                outcome,
                qualityFlag,
                message,
                facilityId);
    }

    private static Map<String, Object> buildQualitySummary(
            List<FacilityMasterImportDtos.MasterFacilitySeedRecord> records,
            List<FacilityMasterImportDtos.FacilityMasterImportRowResult> results,
            int warnings) {
        Map<String, FacilityMasterImportDtos.MasterFacilitySeedRecord> byUid = new HashMap<>();
        for (FacilityMasterImportDtos.MasterFacilitySeedRecord r : records) {
            byUid.put(r.facilityUid(), r);
        }
        Set<String> importedOutcomes = Set.of("CREATED", "UPDATED", "WOULD_CREATE", "WOULD_UPDATE");

        long missingCode = countByFlag(results, "EXCLUDED_MISSING_FACILITY_CODE");
        long duplicateCode = countByFlag(results, "EXCLUDED_DUPLICATE_FACILITY_CODE");
        long duplicateName = countByFlag(results, "EXCLUDED_DUPLICATE_FACILITY_NAME");
        long importedRows = results.stream().filter(r -> importedOutcomes.contains(r.outcome())).count();
        long failedRows = results.stream().filter(r -> "FAILED".equals(r.outcome())).count();
        // Acceptable-missing = rows that WERE import-eligible (imported/would-import) yet still carry an
        // acceptable missing field (coords/type/ownership/status). Surfaced so the gap stays visible.
        long acceptableMissing = results.stream()
                .filter(r -> importedOutcomes.contains(r.outcome()))
                .map(r -> byUid.get(r.facilityUid()))
                .filter(java.util.Objects::nonNull)
                .filter(FacilityMasterImportService::hasAcceptableMissingField)
                .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pack_id", PACK_ID);
        summary.put("source_label", SOURCE_LABEL);
        summary.put("records_total", records.size());
        summary.put("records_imported", importedRows);
        summary.put("records_open", records.stream().filter(r -> "open".equalsIgnoreCase(r.status())).count());
        summary.put("records_with_valid_coordinates", records.stream().filter(FacilityMasterImportService::hasValidCoordinates).count());
        summary.put("records_missing_facility_code", missingCode);
        summary.put("records_duplicate_facility_code", duplicateCode);
        summary.put("records_duplicate_facility_name", duplicateName);
        summary.put("records_excluded_total", missingCode + duplicateCode + duplicateName);
        summary.put("records_acceptable_missing", acceptableMissing);
        summary.put("records_failed", failedRows);
        summary.put("warnings_emitted", warnings);
        return summary;
    }

    private static long countByFlag(
            List<FacilityMasterImportDtos.FacilityMasterImportRowResult> results, String flag) {
        return results.stream().filter(r -> flag.equals(r.qualityFlag())).count();
    }

    /** True when an import-eligible row still carries an acceptable-missing field (kept visible, not faked). */
    private static boolean hasAcceptableMissingField(FacilityMasterImportDtos.MasterFacilitySeedRecord r) {
        return isBlank(r.facilityType()) || isBlank(r.ownership()) || isBlank(r.status())
                || !hasValidCoordinates(r);
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** Minimal RFC-4180-ish CSV line splitter: handles quoted fields and escaped double-quotes. */
    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    private static String cell(List<String> fields, Map<String, Integer> idx, String column) {
        Integer i = idx.get(column);
        if (i == null || i >= fields.size()) {
            return null;
        }
        String v = fields.get(i);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String firstNonBlank(String a, String b) {
        return !isBlank(a) ? a : (!isBlank(b) ? b : null);
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    private static Integer intOrNull(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double doubleOrNull(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Double.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    private static Double doubleOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(v));
    }

    private record ImportDecision(
            Optional<FacilityEntity> existing,
            String dryRunOutcome,
            String warning,
            String message,
            String resolvedCode,
            Long facilityId) {}
}
