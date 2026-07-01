package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityMasterImportDtos;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityContactEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGeoEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierSystem;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityContactRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGeoRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
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
    private final ObjectMapper objectMapper;

    public FacilityMasterImportService(
            FacilityRepository facilityRepository,
            FacilityIdentifierRepository identifierRepository,
            FacilityContactRepository contactRepository,
            FacilityGeoRepository geoRepository,
            ObjectMapper objectMapper) {
        this.facilityRepository = facilityRepository;
        this.identifierRepository = identifierRepository;
        this.contactRepository = contactRepository;
        this.geoRepository = geoRepository;
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
            records.add(new FacilityMasterImportDtos.MasterFacilitySeedRecord(
                    correlationUid,
                    cell(f, idx, "facility_code"),
                    cell(f, idx, "facility_name"),
                    cell(f, idx, "province"),
                    cell(f, idx, "district"),
                    // Prefer canonical classifications; the raw columns remain in the source CSV for audit.
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
                    SOURCE_LABEL_DATE));
        }
        return records;
    }

    @Transactional
    public FacilityMasterImportDtos.FacilityMasterImportResponse importPack(
            FacilityMasterImportDtos.FacilityMasterImportRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        List<FacilityMasterImportDtos.FacilityMasterImportRowResult> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        int warnings = 0;

        for (FacilityMasterImportDtos.MasterFacilitySeedRecord record : request.records()) {
            try {
                ImportDecision decision = evaluateRecord(tenantId, record, request.reconcileDuplicateCodes());
                if (decision.warning() != null) {
                    warnings++;
                }
                if (request.dryRun()) {
                    results.add(rowResult(record, decision.dryRunOutcome(), decision.warning(), decision.message(), decision.facilityId()));
                    switch (decision.dryRunOutcome()) {
                        case "WOULD_CREATE" -> created++;
                        case "WOULD_UPDATE" -> updated++;
                        case "SKIPPED" -> skipped++;
                        default -> { }
                    }
                    continue;
                }
                if ("SKIPPED".equals(decision.dryRunOutcome())) {
                    skipped++;
                    results.add(rowResult(record, "SKIPPED", decision.warning(), decision.message(), decision.facilityId()));
                    continue;
                }

                FacilityEntity facility = decision.existing().orElseGet(FacilityEntity::new);
                boolean isNew = facility.getId() == null;
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
                } else {
                    updated++;
                    results.add(rowResult(record, "UPDATED", decision.warning(), null, facility.getId()));
                }
            } catch (Exception e) {
                failed++;
                warnings++;
                results.add(rowResult(record, "FAILED", "DOWNSTREAM", e.getMessage(), null));
                log.warn("Master pack import failed for {}: {}", record.facilityUid(), e.getMessage());
            }
        }

        Map<String, Object> qualitySummary = buildQualitySummary(request.records(), warnings);
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

    private ImportDecision evaluateRecord(
            UUID tenantId,
            FacilityMasterImportDtos.MasterFacilitySeedRecord record,
            boolean reconcileDuplicateCodes) {
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
            int warnings) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pack_id", PACK_ID);
        summary.put("records_total", records.size());
        summary.put("records_open", records.stream().filter(r -> "open".equalsIgnoreCase(r.status())).count());
        summary.put("records_with_valid_coordinates", records.stream().filter(FacilityMasterImportService::hasValidCoordinates).count());
        summary.put("records_missing_facility_code", records.stream().filter(r -> r.facilityCode() == null || r.facilityCode().isBlank()).count());
        summary.put("warnings_emitted", warnings);
        return summary;
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
