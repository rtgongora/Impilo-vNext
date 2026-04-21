package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCapabilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityContactEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGeoEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityHistoryEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityReadinessEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.WorkspaceEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCapabilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityContactRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityGeoRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityHistoryRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityReadinessRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.WorkspaceRepository;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Core service for managing facilities in the TUSO Facility Registry.
 *
 * <p>Handles creation, update, search, closure, and merge operations on facilities,
 * along with their related data (identifiers, contacts, geo, capabilities, readiness).
 * Every mutation records history and publishes events via the outbox pattern.</p>
 */
@Service
public class FacilityService {

    private static final Logger log = LoggerFactory.getLogger(FacilityService.class);

    private final FacilityRepository facilityRepository;
    private final FacilityIdentifierRepository identifierRepository;
    private final FacilityContactRepository contactRepository;
    private final FacilityGeoRepository geoRepository;
    private final FacilityCapabilityRepository capabilityRepository;
    private final FacilityReadinessRepository readinessRepository;
    private final FacilityHistoryRepository historyRepository;
    private final WorkspaceRepository workspaceRepository;
    private final EventOutboxRepository outboxRepository;

    public FacilityService(FacilityRepository facilityRepository,
                           FacilityIdentifierRepository identifierRepository,
                           FacilityContactRepository contactRepository,
                           FacilityGeoRepository geoRepository,
                           FacilityCapabilityRepository capabilityRepository,
                           FacilityReadinessRepository readinessRepository,
                           FacilityHistoryRepository historyRepository,
                           WorkspaceRepository workspaceRepository,
                           EventOutboxRepository outboxRepository) {
        this.facilityRepository = facilityRepository;
        this.identifierRepository = identifierRepository;
        this.contactRepository = contactRepository;
        this.geoRepository = geoRepository;
        this.capabilityRepository = capabilityRepository;
        this.readinessRepository = readinessRepository;
        this.historyRepository = historyRepository;
        this.workspaceRepository = workspaceRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Create a new facility with all related data.
     *
     * @param dto the facility creation data
     * @return the persisted facility entity
     */
    @Transactional
    public FacilityEntity createFacility(CreateFacilityRequest dto) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        log.info("Creating facility '{}' for tenant {}", dto.name(), tenantId);

        FacilityEntity facility = new FacilityEntity();
        facility.setTenantId(tenantId);
        facility.setFacilityCode(dto.facilityCode());
        facility.setName(dto.name());
        facility.setFacilityType(dto.facilityType());
        facility.setProvince(dto.province());
        facility.setDistrict(dto.district());
        facility.setLatitude(dto.latitude());
        facility.setLongitude(dto.longitude());
        facility.setStatus("ACTIVE");
        facility.setOperationalStatus(dto.operationalStatus() != null ? dto.operationalStatus() : "OPERATIONAL");
        facility.setOwnership(dto.ownership());
        facility.setLevel(dto.level());
        facility.setFacilityTier(dto.facilityTier());
        facility.setDeploymentMode(dto.deploymentMode());
        facility.setContinuityClass(dto.continuityClass());
        facility.setWorkflowArchetype(dto.workflowArchetype());
        facility.setDescription(dto.description());
        facility.setOpenedDate(dto.openedDate());
        facility.setRegulatoryStatus(FacilityRegulatoryStatus.REGISTERED_ACTIVE);
        facility.setRegulatoryStatusUpdatedAt(Instant.now());
        facility.setVersion(1);
        facility.setCreatedBy(actorId);
        facility.setUpdatedBy(actorId);

        if (dto.parentId() != null) {
            FacilityEntity parent = facilityRepository.findById(dto.parentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent facility not found: " + dto.parentId()));
            facility.setParent(parent);
        }

        facility = facilityRepository.save(facility);
        Long facilityId = facility.getId();

        // Persist identifiers
        if (dto.identifiers() != null) {
            for (IdentifierData ident : dto.identifiers()) {
                FacilityIdentifierEntity identEntity = new FacilityIdentifierEntity();
                identEntity.setFacility(facility);
                identEntity.setSystem(ident.system());
                identEntity.setValue(ident.value());
                identEntity.setActive(true);
                identifierRepository.save(identEntity);
            }
        }

        // Persist contacts
        if (dto.contacts() != null) {
            for (ContactData contact : dto.contacts()) {
                FacilityContactEntity contactEntity = new FacilityContactEntity();
                contactEntity.setFacility(facility);
                contactEntity.setContactType(contact.contactType());
                contactEntity.setName(contact.name());
                contactEntity.setPhone(contact.phone());
                contactEntity.setEmail(contact.email());
                contactEntity.setRole(contact.role());
                contactRepository.save(contactEntity);
            }
        }

        // Persist geo
        if (dto.geo() != null) {
            GeoData geo = dto.geo();
            FacilityGeoEntity geoEntity = new FacilityGeoEntity();
            geoEntity.setFacility(facility);
            geoEntity.setAddressLine1(geo.addressLine1());
            geoEntity.setAddressLine2(geo.addressLine2());
            geoEntity.setCity(geo.city());
            geoEntity.setProvince(geo.province());
            geoEntity.setDistrict(geo.district());
            geoEntity.setWard(geo.ward());
            geoEntity.setPostalCode(geo.postalCode());
            geoEntity.setCountry(geo.country() != null ? geo.country() : "ZWE");
            geoEntity.setAltitudeM(geo.altitudeM());
            geoEntity.setCatchmentArea(geo.catchmentArea());
            geoRepository.save(geoEntity);
        }

        // Persist capabilities
        if (dto.capabilities() != null) {
            for (CapabilityData cap : dto.capabilities()) {
                FacilityCapabilityEntity capEntity = new FacilityCapabilityEntity();
                capEntity.setFacility(facility);
                capEntity.setTenantId(tenantId);
                capEntity.setCapabilityCode(cap.capabilityCode());
                capEntity.setCapabilityType(cap.capabilityType());
                capEntity.setName(cap.name());
                capEntity.setActive(true);
                capEntity.setOperatingHours(null);
                capEntity.setMetadata(null);
                capabilityRepository.save(capEntity);
            }
        }

        // Persist readiness
        if (dto.readiness() != null) {
            ReadinessData r = dto.readiness();
            FacilityReadinessEntity readinessEntity = new FacilityReadinessEntity();
            readinessEntity.setFacility(facility);
            readinessEntity.setConnectivity(r.connectivity());
            readinessEntity.setPowerSource(r.powerSource());
            readinessEntity.setPowerBackup(r.powerBackup() != null && r.powerBackup());
            readinessEntity.setDeviceCount(r.deviceCount() != null ? r.deviceCount() : 0);
            readinessEntity.setEhrReady(r.ehrReady() != null && r.ehrReady());
            readinessEntity.setComplianceFlags(null);
            readinessEntity.setAssessedBy(actorId);
            readinessEntity.setAssessedAt(Instant.now());
            readinessRepository.save(readinessEntity);
        }

        // Record history
        recordHistory(facilityId, "CREATE", null, null, null, actorId, "Facility created");

        // Publish outbox event
        publishEvent("FACILITY", facilityId.toString(), "tuso.facility.created",
                buildFacilityPayload(facility, "CREATED"));

        log.info("Facility '{}' created with id {} for tenant {}", dto.name(), facilityId, tenantId);
        return facility;
    }

    /**
     * Update facility attributes. Records history for each changed field
     * and increments the version number.
     *
     * @param id  the facility ID
     * @param dto the update data
     * @return the updated facility entity
     */
    @Transactional
    public FacilityEntity updateFacility(Long id, UpdateFacilityRequest dto) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        FacilityEntity facility = facilityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + id));

        if (!facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation: facility belongs to different tenant");
        }

        log.info("Updating facility {} for tenant {}", id, tenantId);

        // Track changes for history
        if (dto.name() != null && !dto.name().equals(facility.getName())) {
            recordHistory(id, "UPDATE", "name", facility.getName(), dto.name(), actorId, null);
            facility.setName(dto.name());
        }
        if (dto.facilityType() != null && !dto.facilityType().equals(facility.getFacilityType())) {
            recordHistory(id, "UPDATE", "facility_type", facility.getFacilityType(), dto.facilityType(), actorId, null);
            facility.setFacilityType(dto.facilityType());
        }
        if (dto.province() != null && !dto.province().equals(facility.getProvince())) {
            recordHistory(id, "UPDATE", "province", facility.getProvince(), dto.province(), actorId, null);
            facility.setProvince(dto.province());
        }
        if (dto.district() != null && !dto.district().equals(facility.getDistrict())) {
            recordHistory(id, "UPDATE", "district", facility.getDistrict(), dto.district(), actorId, null);
            facility.setDistrict(dto.district());
        }
        if (dto.operationalStatus() != null && !dto.operationalStatus().equals(facility.getOperationalStatus())) {
            recordHistory(id, "UPDATE", "operational_status", facility.getOperationalStatus(), dto.operationalStatus(), actorId, null);
            facility.setOperationalStatus(dto.operationalStatus());
        }
        if (dto.ownership() != null && !dto.ownership().equals(facility.getOwnership())) {
            recordHistory(id, "UPDATE", "ownership", facility.getOwnership(), dto.ownership(), actorId, null);
            facility.setOwnership(dto.ownership());
        }
        if (dto.level() != null && !dto.level().equals(facility.getLevel())) {
            recordHistory(id, "UPDATE", "level", facility.getLevel(), dto.level(), actorId, null);
            facility.setLevel(dto.level());
        }
        if (dto.facilityTier() != null && !dto.facilityTier().equals(facility.getFacilityTier())) {
            recordHistory(id, "UPDATE", "facility_tier", facility.getFacilityTier(), dto.facilityTier(), actorId, null);
            facility.setFacilityTier(dto.facilityTier());
        }
        if (dto.deploymentMode() != null && !dto.deploymentMode().equals(facility.getDeploymentMode())) {
            recordHistory(id, "UPDATE", "deployment_mode", facility.getDeploymentMode(), dto.deploymentMode(), actorId, null);
            facility.setDeploymentMode(dto.deploymentMode());
        }
        if (dto.continuityClass() != null && !dto.continuityClass().equals(facility.getContinuityClass())) {
            recordHistory(id, "UPDATE", "continuity_class", facility.getContinuityClass(), dto.continuityClass(), actorId, null);
            facility.setContinuityClass(dto.continuityClass());
        }
        if (dto.workflowArchetype() != null && !dto.workflowArchetype().equals(facility.getWorkflowArchetype())) {
            recordHistory(id, "UPDATE", "workflow_archetype", facility.getWorkflowArchetype(), dto.workflowArchetype(), actorId, null);
            facility.setWorkflowArchetype(dto.workflowArchetype());
        }
        if (dto.description() != null && !dto.description().equals(facility.getDescription())) {
            recordHistory(id, "UPDATE", "description", facility.getDescription(), dto.description(), actorId, null);
            facility.setDescription(dto.description());
        }
        if (dto.latitude() != null && !dto.latitude().equals(facility.getLatitude())) {
            recordHistory(id, "UPDATE", "latitude",
                    facility.getLatitude() != null ? facility.getLatitude().toString() : null,
                    dto.latitude().toString(), actorId, null);
            facility.setLatitude(dto.latitude());
        }
        if (dto.longitude() != null && !dto.longitude().equals(facility.getLongitude())) {
            recordHistory(id, "UPDATE", "longitude",
                    facility.getLongitude() != null ? facility.getLongitude().toString() : null,
                    dto.longitude().toString(), actorId, null);
            facility.setLongitude(dto.longitude());
        }

        facility.setVersion(facility.getVersion() + 1);
        facility.setUpdatedBy(actorId);
        facility = facilityRepository.save(facility);

        publishEvent("FACILITY", id.toString(), "tuso.facility.updated",
                buildFacilityPayload(facility, "UPDATED"));

        log.info("Facility {} updated to version {}", id, facility.getVersion());
        return facility;
    }

    /**
     * Get a facility with all related data (identifiers, contacts, geo, capabilities, readiness).
     *
     * @param id the facility ID
     * @return the facility detail result
     */
    @Transactional(readOnly = true)
    public FacilityDetail getFacility(Long id) {
        FacilityEntity facility = facilityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + id));

        List<FacilityIdentifierEntity> identifiers = identifierRepository.findByFacilityId(id);
        List<FacilityContactEntity> contacts = contactRepository.findByFacilityId(id);
        FacilityGeoEntity geo = geoRepository.findByFacilityId(id).orElse(null);
        List<FacilityCapabilityEntity> capabilities = capabilityRepository.findByFacilityIdAndActiveTrue(id);
        FacilityReadinessEntity readiness = readinessRepository.findByFacilityId(id).orElse(null);

        return new FacilityDetail(facility, identifiers, contacts, geo, capabilities, readiness);
    }

    /**
     * Search facilities by name (trigram), type, status, district, and province. Results are paged.
     *
     * @param tenantId the tenant UUID for isolation
     * @param query    optional name search query (trigram-based)
     * @param filters  optional search filters
     * @param pageable pagination parameters
     * @return page of matching facilities
     */
    @Transactional(readOnly = true)
    public Page<FacilityEntity> searchFacilities(UUID tenantId, String query,
                                                  FacilitySearchFilters filters,
                                                  Pageable pageable) {
        String type = filters != null ? filters.facilityType() : null;
        String status = filters != null ? filters.status() : null;
        String district = filters != null ? filters.district() : null;
        String province = filters != null ? filters.province() : null;
        FacilityRegulatoryStatus regulatoryStatus = null;

        if (query != null && !query.isBlank()) {
            log.debug("Searching facilities for tenant {} with query '{}', type={}, status={}, district={}, province={}",
                    tenantId, query, type, status, district, province);
            return facilityRepository.searchByNameAndFilters(tenantId, query.trim(), type, status,
                    regulatoryStatus, district, province, pageable);
        }

        log.debug("Listing facilities for tenant {} with type={}, status={}, district={}, province={}",
                tenantId, type, status, district, province);
        return facilityRepository.findByFilters(tenantId, type, status, regulatoryStatus, district, province, pageable);
    }

    /**
     * Close a facility. Sets status to CLOSED, records the closure date and reason.
     *
     * @param id     the facility ID
     * @param reason the reason for closure
     * @return the closed facility entity
     */
    @Transactional
    public FacilityEntity closeFacility(Long id, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        FacilityEntity facility = facilityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + id));

        if (!facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation: facility belongs to different tenant");
        }

        if ("CLOSED".equals(facility.getStatus())) {
            throw new IllegalStateException("Facility is already closed");
        }

        log.info("Closing facility {} for tenant {}, reason: {}", id, tenantId, reason);

        String oldStatus = facility.getStatus();
        facility.setStatus("CLOSED");
        facility.setClosedDate(LocalDate.now());
        facility.setCloseReason(reason);
        facility.setVersion(facility.getVersion() + 1);
        facility.setUpdatedBy(actorId);
        facility = facilityRepository.save(facility);

        recordHistory(id, "CLOSE", "status", oldStatus, "CLOSED", actorId, reason);

        publishEvent("FACILITY", id.toString(), "tuso.facility.closed",
                buildFacilityPayload(facility, "CLOSED"));

        log.info("Facility {} closed successfully", id);
        return facility;
    }

    /**
     * Merge a source facility into a target facility.
     * Sets source status to MERGED, transfers all active workspaces to the target.
     *
     * @param sourceId the facility being merged (source)
     * @param targetId the facility receiving the merge (target)
     * @return the merged (source) facility entity
     */
    @Transactional
    public FacilityEntity mergeFacility(Long sourceId, Long targetId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot merge a facility into itself");
        }

        FacilityEntity source = facilityRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Source facility not found: " + sourceId));
        FacilityEntity target = facilityRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("Target facility not found: " + targetId));

        if (!source.getTenantId().equals(tenantId) || !target.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation: facilities belong to different tenant");
        }

        if ("MERGED".equals(source.getStatus()) || "CLOSED".equals(source.getStatus())) {
            throw new IllegalStateException("Source facility is already " + source.getStatus());
        }

        log.info("Merging facility {} into {} for tenant {}", sourceId, targetId, tenantId);

        // Transfer active workspaces from source to target
        List<WorkspaceEntity> workspaces = workspaceRepository.findByFacilityIdAndActiveTrue(sourceId);
        for (WorkspaceEntity ws : workspaces) {
            ws.setFacility(target);
            ws.setUpdatedBy(actorId);
            workspaceRepository.save(ws);
        }
        log.info("Transferred {} active workspaces from facility {} to {}", workspaces.size(), sourceId, targetId);

        // Update source facility
        String oldStatus = source.getStatus();
        source.setStatus("MERGED");
        source.setMergedInto(target);
        source.setVersion(source.getVersion() + 1);
        source.setUpdatedBy(actorId);
        source = facilityRepository.save(source);

        recordHistory(sourceId, "MERGE", "status", oldStatus, "MERGED", actorId,
                "Merged into facility " + targetId);
        recordHistory(targetId, "MERGE_RECEIVE", null, null, null, actorId,
                "Received merge from facility " + sourceId);

        publishEvent("FACILITY", sourceId.toString(), "tuso.facility.merged",
                String.format("{\"sourceId\":%d,\"targetId\":%d,\"tenantId\":\"%s\",\"action\":\"MERGED\"," +
                        "\"workspacesTransferred\":%d,\"actorId\":\"%s\"}",
                        sourceId, targetId, tenantId, workspaces.size(), actorId));

        log.info("Facility {} merged into {} successfully", sourceId, targetId);
        return source;
    }

    // ---- Private helpers ----

    private void recordHistory(Long facilityId, String changeType, String fieldName,
                                String oldValue, String newValue, String changedBy, String reason) {
        FacilityHistoryEntity history = new FacilityHistoryEntity();
        history.setFacility(facilityRepository.getReferenceById(facilityId));
        history.setChangeType(changeType);
        history.setFieldName(fieldName);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setChangedBy(changedBy);
        history.setReason(reason);
        historyRepository.save(history);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    private String buildFacilityPayload(FacilityEntity facility, String action) {
        return String.format(
                "{\"facilityId\":%d,\"tenantId\":\"%s\",\"facilityCode\":\"%s\",\"name\":\"%s\"," +
                "\"status\":\"%s\",\"version\":%d,\"action\":\"%s\"}",
                facility.getId(), facility.getTenantId(), facility.getFacilityCode(),
                facility.getName(), facility.getStatus(), facility.getVersion(), action);
    }

    // ---- DTOs ----

    public record CreateFacilityRequest(
            String facilityCode,
            String name,
            String facilityType,
            String province,
            String district,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            String operationalStatus,
            String ownership,
            String level,
            String facilityTier,
            String deploymentMode,
            String continuityClass,
            String workflowArchetype,
            String description,
            LocalDate openedDate,
            Long parentId,
            List<IdentifierData> identifiers,
            List<ContactData> contacts,
            GeoData geo,
            List<CapabilityData> capabilities,
            ReadinessData readiness
    ) {}

    public record UpdateFacilityRequest(
            String name,
            String facilityType,
            String province,
            String district,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            String operationalStatus,
            String ownership,
            String level,
            String facilityTier,
            String deploymentMode,
            String continuityClass,
            String workflowArchetype,
            String description
    ) {}

    public record IdentifierData(String system, String value) {}

    public record ContactData(String contactType, String name, String phone, String email, String role) {}

    public record GeoData(
            String addressLine1, String addressLine2, String city, String province,
            String district, String ward, String postalCode, String country,
            java.math.BigDecimal altitudeM, String catchmentArea
    ) {}

    public record CapabilityData(
            String capabilityCode, String capabilityType, String name,
            String operatingHours, String metadata
    ) {}

    public record ReadinessData(
            String connectivity, String powerSource, Boolean powerBackup,
            Integer deviceCount, Boolean ehrReady, String complianceFlags
    ) {}

    public record FacilitySearchFilters(
            String facilityType, String status, String district, String province
    ) {}

    public record FacilityDetail(
            FacilityEntity facility,
            List<FacilityIdentifierEntity> identifiers,
            List<FacilityContactEntity> contacts,
            FacilityGeoEntity geo,
            List<FacilityCapabilityEntity> capabilities,
            FacilityReadinessEntity readiness
    ) {}
}
