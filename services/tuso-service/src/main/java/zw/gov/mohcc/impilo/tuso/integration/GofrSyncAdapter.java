package zw.gov.mohcc.impilo.tuso.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import zw.gov.mohcc.impilo.tuso.config.TusoProperties;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.GofrSyncLogEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.GofrSyncLogRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * GOFR (Global Open Facility Registry) dual-mode sync adapter.
 *
 * <p>When GOFR mode is enabled, this adapter synchronizes facilities between
 * the local TUSO registry and the external GOFR instance. Supports three operations:
 * <ul>
 *   <li>{@code syncFromGofr} — pull all facilities from GOFR REST API into local registry</li>
 *   <li>{@code exportToGofr} — push a single local facility to GOFR</li>
 *   <li>{@code importFromCsv} — bulk-import facilities from CSV content</li>
 * </ul>
 *
 * <p>All operations are idempotent: upsert by gofr_id or facility_code.
 * Sync results are logged to the {@code gofr_sync_log} table.</p>
 *
 * <p>Uses Spring's {@link RestClient} for HTTP calls to GOFR.</p>
 */
@Service
public class GofrSyncAdapter {

    private static final Logger log = LoggerFactory.getLogger(GofrSyncAdapter.class);

    private final TusoProperties tusoProperties;
    private final FacilityRepository facilityRepository;
    private final GofrSyncLogRepository syncLogRepository;
    private final RestClient restClient;

    public GofrSyncAdapter(TusoProperties tusoProperties,
                           FacilityRepository facilityRepository,
                           GofrSyncLogRepository syncLogRepository,
                           RestClient.Builder restClientBuilder) {
        this.tusoProperties = tusoProperties;
        this.facilityRepository = facilityRepository;
        this.syncLogRepository = syncLogRepository;
        this.restClient = restClientBuilder
                .baseUrl(tusoProperties.getGofr().getBaseUrl())
                .build();
    }

    /**
     * Pull facilities from GOFR REST API and create/update local FacilityEntity records.
     * Maps GOFR fields to TUSO fields. Stores gofr_id on facility.
     * Logs sync results to gofr_sync_log.
     *
     * @param tenantId the tenant UUID for multi-tenant isolation
     * @return the sync log entry recording results
     */
    @Transactional
    public GofrSyncLogEntity syncFromGofr(UUID tenantId) {
        if (!tusoProperties.getGofr().isEnabled()) {
            log.warn("GOFR sync is disabled; skipping pull for tenant {}", tenantId);
            return null;
        }

        log.info("Starting GOFR sync (pull) for tenant {}", tenantId);

        GofrSyncLogEntity syncLog = createSyncLog(tenantId, "FULL", "INBOUND");
        int processed = 0;
        int created = 0;
        int updated = 0;
        int errors = 0;
        Map<String, Object> errorDetails = new HashMap<>();

        try {
            List<Map<String, Object>> gofrFacilities = fetchFacilitiesFromGofr();

            for (Map<String, Object> gofrFacility : gofrFacilities) {
                processed++;
                try {
                    boolean isNew = upsertFacilityFromGofr(tenantId, gofrFacility);
                    if (isNew) {
                        created++;
                    } else {
                        updated++;
                    }
                } catch (Exception e) {
                    errors++;
                    errorDetails.put("entry_" + processed, e.getMessage());
                    log.warn("Error processing GOFR entry {}: {}", processed, e.getMessage());
                }
            }

            completeSyncLog(syncLog, "COMPLETED", processed, created, updated, errors,
                    errors > 0 ? errorDetails : null);
            log.info("GOFR sync completed for tenant {}: processed={}, created={}, updated={}, errors={}",
                    tenantId, processed, created, updated, errors);

        } catch (RestClientException e) {
            log.error("GOFR sync failed for tenant {}: {}", tenantId, e.getMessage());
            completeSyncLog(syncLog, "FAILED", 0, 0, 0, 1,
                    Map.of("connection_error", e.getMessage()));
        }

        return syncLog;
    }

    /**
     * Push a local facility to GOFR (for new facilities created in TUSO).
     *
     * @param tenantId   the tenant UUID
     * @param facilityId the local facility ID to export
     * @return the sync log entry recording the export result
     */
    @Transactional
    public GofrSyncLogEntity exportToGofr(UUID tenantId, Long facilityId) {
        if (!tusoProperties.getGofr().isEnabled()) {
            log.warn("GOFR sync is disabled; skipping export for facility {}", facilityId);
            return null;
        }

        log.info("Exporting facility {} to GOFR for tenant {}", facilityId, tenantId);

        GofrSyncLogEntity syncLog = createSyncLog(tenantId, "SINGLE", "OUTBOUND");

        try {
            FacilityEntity facility = facilityRepository.findById(facilityId)
                    .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

            if (!facility.getTenantId().equals(tenantId)) {
                throw new SecurityException("Tenant isolation violation: facility belongs to different tenant");
            }

            Map<String, Object> fhirLocation = buildFhirLocation(facility);

            if (facility.getGofrId() != null) {
                restClient.put()
                        .uri("/fhir/Location/{gofrId}", facility.getGofrId())
                        .body(fhirLocation)
                        .retrieve()
                        .toBodilessEntity();
                completeSyncLog(syncLog, "COMPLETED", 1, 0, 1, 0, null);
            } else {
                Map<String, Object> result = restClient.post()
                        .uri("/fhir/Location")
                        .body(fhirLocation)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});

                if (result != null && result.containsKey("id")) {
                    String gofrId = result.get("id").toString();
                    facility.setGofrId(gofrId);
                    facilityRepository.save(facility);
                    log.info("Facility {} exported to GOFR with gofr_id={}", facilityId, gofrId);
                }
                completeSyncLog(syncLog, "COMPLETED", 1, 1, 0, 0, null);
            }

        } catch (Exception e) {
            log.error("Failed to export facility {} to GOFR: {}", facilityId, e.getMessage(), e);
            completeSyncLog(syncLog, "FAILED", 1, 0, 0, 1,
                    Map.of("facilityId", facilityId.toString(), "error", e.getMessage()));
        }

        return syncLog;
    }

    /**
     * Parse CSV content and create/update facilities.
     * Expected CSV columns: name, facility_code, type, province, district, lat, lng.
     * First line is treated as header and skipped.
     * Upserts by facility_code within the tenant.
     *
     * @param tenantId   the tenant UUID
     * @param csvContent the raw CSV string
     * @return the sync log entity recording import results
     */
    @Transactional
    public GofrSyncLogEntity importFromCsv(UUID tenantId, String csvContent) {
        log.info("Starting CSV import for tenant {}", tenantId);

        GofrSyncLogEntity syncLog = createSyncLog(tenantId, "CSV", "INBOUND");
        int processed = 0;
        int created = 0;
        int updated = 0;
        int errors = 0;
        List<Map<String, Object>> errorDetailsList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                completeSyncLog(syncLog, "COMPLETED", 0, 0, 0, 0, null);
                return syncLog;
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                processed++;
                try {
                    String[] columns = line.split(",", -1);
                    if (columns.length < 7) {
                        throw new IllegalArgumentException(
                                "Expected 7 columns (name, facility_code, type, province, district, lat, lng), got " + columns.length);
                    }

                    String name = columns[0].trim();
                    String facilityCode = columns[1].trim();
                    String facilityType = columns[2].trim();
                    String province = columns[3].trim();
                    String district = columns[4].trim();
                    BigDecimal latitude = parseBigDecimalOrNull(columns[5].trim());
                    BigDecimal longitude = parseBigDecimalOrNull(columns[6].trim());

                    if (facilityCode.isBlank()) {
                        throw new IllegalArgumentException("facility_code is required");
                    }

                    Optional<FacilityEntity> existing = facilityRepository
                            .findByTenantIdAndFacilityCode(tenantId, facilityCode);

                    if (existing.isPresent()) {
                        FacilityEntity facility = existing.get();
                        facility.setName(name);
                        facility.setFacilityType(facilityType);
                        facility.setProvince(province);
                        facility.setDistrict(district);
                        if (latitude != null) facility.setLatitude(latitude);
                        if (longitude != null) facility.setLongitude(longitude);
                        facility.setVersion(facility.getVersion() + 1);
                        facility.setUpdatedBy("CSV_IMPORT");
                        facilityRepository.save(facility);
                        updated++;
                    } else {
                        FacilityEntity facility = new FacilityEntity();
                        facility.setTenantId(tenantId);
                        facility.setFacilityCode(facilityCode);
                        facility.setName(name);
                        facility.setFacilityType(facilityType);
                        facility.setProvince(province);
                        facility.setDistrict(district);
                        facility.setLatitude(latitude);
                        facility.setLongitude(longitude);
                        facility.setStatus("ACTIVE");
                        facility.setOperationalStatus("OPERATIONAL");
                        facility.setVersion(1);
                        facility.setCreatedBy("CSV_IMPORT");
                        facility.setUpdatedBy("CSV_IMPORT");
                        facilityRepository.save(facility);
                        created++;
                    }
                } catch (Exception e) {
                    errors++;
                    Map<String, Object> errorDetail = new HashMap<>();
                    errorDetail.put("line", lineNumber);
                    errorDetail.put("content", line);
                    errorDetail.put("error", e.getMessage());
                    errorDetailsList.add(errorDetail);
                    log.warn("CSV import error at line {}: {}", lineNumber, e.getMessage());
                }
            }

            Map<String, Object> allErrors = errorDetailsList.isEmpty() ? null : Map.of("errors", errorDetailsList);
            completeSyncLog(syncLog, "COMPLETED", processed, created, updated, errors, allErrors);
        } catch (IOException e) {
            log.error("Failed to read CSV content: {}", e.getMessage(), e);
            completeSyncLog(syncLog, "FAILED", processed, created, updated, errors + 1,
                    Map.of("phase", "read", "error", e.getMessage()));
        }

        log.info("CSV import completed for tenant {}: processed={}, created={}, updated={}, errors={}",
                tenantId, processed, created, updated, errors);

        return syncLog;
    }

    // ---- Private helpers ----

    private GofrSyncLogEntity createSyncLog(UUID tenantId, String syncType, String direction) {
        GofrSyncLogEntity syncLog = new GofrSyncLogEntity();
        syncLog.setTenantId(tenantId);
        syncLog.setSyncType(syncType);
        syncLog.setDirection(direction);
        syncLog.setStatus("RUNNING");
        syncLog.setStartedAt(Instant.now());
        return syncLogRepository.save(syncLog);
    }

    private void completeSyncLog(GofrSyncLogEntity syncLog, String status,
                                  int processed, int created, int updated, int errors,
                                  Map<String, Object> errorDetails) {
        syncLog.setStatus(status);
        syncLog.setFacilitiesProcessed(processed);
        syncLog.setFacilitiesCreated(created);
        syncLog.setFacilitiesUpdated(updated);
        syncLog.setErrors(errors);
        syncLog.setErrorDetails(errorDetails);
        syncLog.setCompletedAt(Instant.now());
        syncLogRepository.save(syncLog);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchFacilitiesFromGofr() {
        Map<String, Object> response = restClient.get()
                .uri("/fhir/Location?_count=1000")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null || !response.containsKey("entry")) {
            return List.of();
        }

        Object entries = response.get("entry");
        if (entries instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }

        return List.of();
    }

    /**
     * Upsert a facility from GOFR data. Returns true if newly created, false if updated.
     */
    @SuppressWarnings("unchecked")
    private boolean upsertFacilityFromGofr(UUID tenantId, Map<String, Object> entry) {
        Map<String, Object> resource = (Map<String, Object>) entry.get("resource");
        if (resource == null) {
            resource = entry;
        }

        String gofrId = getStringField(resource, "id");
        String name = getStringField(resource, "name");
        String facilityCode = extractFacilityCode(resource);
        String status = extractStatus(resource);
        BigDecimal latitude = null;
        BigDecimal longitude = null;

        Object position = resource.get("position");
        if (position instanceof Map<?, ?> posMap) {
            latitude = getBigDecimalField((Map<String, Object>) posMap, "latitude");
            longitude = getBigDecimalField((Map<String, Object>) posMap, "longitude");
        }

        if (gofrId == null || gofrId.isBlank()) {
            throw new IllegalArgumentException("GOFR facility missing required 'id' field");
        }

        Optional<FacilityEntity> existingByGofrId = facilityRepository.findByTenantIdAndGofrId(tenantId, gofrId);
        Optional<FacilityEntity> existingByCode = (facilityCode != null && !facilityCode.isBlank())
                ? facilityRepository.findByTenantIdAndFacilityCode(tenantId, facilityCode)
                : Optional.empty();

        FacilityEntity facility;
        boolean isNew;

        if (existingByGofrId.isPresent()) {
            facility = existingByGofrId.get();
            isNew = false;
        } else if (existingByCode.isPresent()) {
            facility = existingByCode.get();
            isNew = false;
        } else {
            facility = new FacilityEntity();
            facility.setTenantId(tenantId);
            facility.setFacilityCode(facilityCode != null ? facilityCode : "GOFR-" + gofrId);
            facility.setVersion(0);
            facility.setCreatedBy("GOFR_SYNC");
            isNew = true;
        }

        facility.setGofrId(gofrId);
        if (name != null) facility.setName(name);
        if (status != null) facility.setStatus(status);
        if (latitude != null) facility.setLatitude(latitude);
        if (longitude != null) facility.setLongitude(longitude);

        facility.setVersion(facility.getVersion() + 1);
        facility.setUpdatedBy("GOFR_SYNC");

        facilityRepository.save(facility);

        return isNew;
    }

    private String extractStatus(Map<String, Object> resource) {
        Object statusObj = resource.get("status");
        if (statusObj instanceof String s) {
            return "active".equalsIgnoreCase(s) ? "ACTIVE" : "INACTIVE";
        }
        return "ACTIVE";
    }

    @SuppressWarnings("unchecked")
    private String extractFacilityCode(Map<String, Object> resource) {
        Object identifiers = resource.get("identifier");
        if (identifiers instanceof List<?> list) {
            for (Object ident : list) {
                if (ident instanceof Map<?, ?> identMap) {
                    String system = getStringField((Map<String, Object>) identMap, "system");
                    if ("urn:impilo:facility-code".equals(system)) {
                        return getStringField((Map<String, Object>) identMap, "value");
                    }
                }
            }
        }
        return null;
    }

    private Map<String, Object> buildFhirLocation(FacilityEntity facility) {
        Map<String, Object> location = new HashMap<>();
        location.put("resourceType", "Location");
        location.put("name", facility.getName());
        location.put("status", "ACTIVE".equals(facility.getStatus()) ? "active" : "inactive");

        if (facility.getLatitude() != null && facility.getLongitude() != null) {
            Map<String, Object> position = new HashMap<>();
            position.put("latitude", facility.getLatitude());
            position.put("longitude", facility.getLongitude());
            location.put("position", position);
        }

        Map<String, Object> identifier = new HashMap<>();
        identifier.put("system", "urn:impilo:facility-code");
        identifier.put("value", facility.getFacilityCode());
        location.put("identifier", List.of(identifier));

        return location;
    }

    private BigDecimal parseBigDecimalOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getStringField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private BigDecimal getBigDecimalField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
