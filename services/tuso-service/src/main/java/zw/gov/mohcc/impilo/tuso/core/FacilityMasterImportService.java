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

    public static final String MASTER_FACILITY_UID_SYSTEM = "MASTER_FACILITY_UID";
    public static final String PACK_ID = "master-health-facility-2024-07-23";
    /** Canonical source-provenance label for this national master dataset. */
    public static final String SOURCE_LABEL = "MASTER_HEALTH_FACILITY_2024_07_23";

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
                upsertIdentifier(facility, record.facilityUid());
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

    private void upsertIdentifier(FacilityEntity facility, String facilityUid) {
        Optional<FacilityIdentifierEntity> existing =
                identifierRepository.findBySystemAndValue(MASTER_FACILITY_UID_SYSTEM, facilityUid);
        if (existing.isPresent()) {
            return;
        }
        FacilityIdentifierEntity ident = new FacilityIdentifierEntity();
        ident.setFacility(facility);
        ident.setSystem(MASTER_FACILITY_UID_SYSTEM);
        ident.setValue(facilityUid);
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
