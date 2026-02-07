package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.api.dto.ConfigVersionResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.EffectiveConfigResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.RollbackConfigRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.UpdateFacilityConfigRequest;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ConfigAuditEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityConfigVersionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ConfigAuditRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityConfigVersionRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for managing facility configuration.
 *
 * <p>Configuration follows a layered merge strategy:
 * tenant defaults → facility overrides → workspace overrides.
 * All changes are versioned, auditable, and support rollback.</p>
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    private final FacilityConfigVersionRepository configVersionRepository;
    private final FacilityRepository facilityRepository;
    private final ConfigAuditRepository configAuditRepository;
    private final EventOutboxRepository outboxRepository;
    private final ConfigResolutionEngine resolutionEngine;

    public ConfigService(FacilityConfigVersionRepository configVersionRepository,
                         FacilityRepository facilityRepository,
                         ConfigAuditRepository configAuditRepository,
                         EventOutboxRepository outboxRepository,
                         ConfigResolutionEngine resolutionEngine) {
        this.configVersionRepository = configVersionRepository;
        this.facilityRepository = facilityRepository;
        this.configAuditRepository = configAuditRepository;
        this.outboxRepository = outboxRepository;
        this.resolutionEngine = resolutionEngine;
    }

    /**
     * Get the effective (merged) configuration for a facility, optionally including workspace overrides.
     */
    @Transactional(readOnly = true)
    public EffectiveConfigResponse getEffectiveConfig(TrustContext ctx, Long facilityId, UUID workspaceId) {
        UUID tenantId = ctx.tenantId();
        log.info("Getting effective config for facility {} workspace {} tenant {}", facilityId, workspaceId, tenantId);

        Map<String, Object> effectiveConfig = resolutionEngine.resolve(tenantId, facilityId, workspaceId);
        return new EffectiveConfigResponse(effectiveConfig);
    }

    /**
     * Update facility configuration by creating a new version.
     * Deactivates the previous active version and audits the change.
     */
    @Transactional
    public ConfigVersionResponse updateConfig(TrustContext ctx, Long facilityId,
                                               UpdateFacilityConfigRequest request) {
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        if (!facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation");
        }

        log.info("Updating config for facility {} by actor {}", facilityId, actorId);

        // Find current active version
        Map<String, Object> oldConfig = null;
        int nextVersion = 1;
        var currentActive = configVersionRepository.findLatestActive(facilityId);
        if (currentActive.isPresent()) {
            FacilityConfigVersionEntity current = currentActive.get();
            oldConfig = current.getConfigData();
            nextVersion = current.getVersion() + 1;
            current.setActive(false);
            configVersionRepository.save(current);
        }

        // Create new version
        FacilityConfigVersionEntity newVersion = new FacilityConfigVersionEntity();
        newVersion.setFacility(facility);
        newVersion.setTenantId(tenantId);
        newVersion.setVersion(nextVersion);
        newVersion.setConfigData(request.configData());
        newVersion.setActive(true);
        newVersion.setCreatedBy(actorId);
        newVersion = configVersionRepository.save(newVersion);

        // Audit
        auditConfigChange("FACILITY", facilityId.toString(), tenantId, "UPDATE", oldConfig, request.configData(), actorId);

        // Outbox event
        publishEvent("CONFIG", facilityId.toString(), "tuso.config.updated",
                String.format("{\"facilityId\":%d,\"tenantId\":\"%s\",\"version\":%d,\"action\":\"CONFIG_UPDATED\"}",
                        facilityId, tenantId, nextVersion));

        log.info("Config for facility {} updated to version {}", facilityId, nextVersion);
        return toResponse(newVersion);
    }

    /**
     * Get the version history for a facility's configuration.
     */
    @Transactional(readOnly = true)
    public List<ConfigVersionResponse> getConfigHistory(TrustContext ctx, Long facilityId) {
        UUID tenantId = ctx.tenantId();
        log.debug("Getting config history for facility {} tenant {}", facilityId, tenantId);

        return configVersionRepository
                .findByFacilityIdOrderByVersionDesc(facilityId, PageRequest.of(0, 50))
                .getContent()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Rollback configuration to a specific previous version.
     * Creates a new version with the data from the target version.
     */
    @Transactional
    public ConfigVersionResponse rollbackConfig(TrustContext ctx, Long facilityId,
                                                  RollbackConfigRequest request) {
        UUID tenantId = ctx.tenantId();
        String actorId = ctx.actorId();

        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        if (!facility.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant isolation violation");
        }

        int targetVersion = request.targetVersion();
        log.info("Rolling back config for facility {} to version {} by actor {}", facilityId, targetVersion, actorId);

        // Find the target version
        var allVersions = configVersionRepository
                .findByFacilityIdOrderByVersionDesc(facilityId, PageRequest.of(0, 1000));
        FacilityConfigVersionEntity targetVersionEntity = allVersions.getContent().stream()
                .filter(v -> v.getVersion() == targetVersion)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Config version " + targetVersion + " not found for facility " + facilityId));

        // Deactivate current active
        Map<String, Object> oldConfig = null;
        int nextVersion = 1;
        var currentActive = configVersionRepository.findLatestActive(facilityId);
        if (currentActive.isPresent()) {
            FacilityConfigVersionEntity current = currentActive.get();
            oldConfig = current.getConfigData();
            nextVersion = current.getVersion() + 1;
            current.setActive(false);
            configVersionRepository.save(current);
        }

        // Create rollback version (new version with old data)
        FacilityConfigVersionEntity rollbackVersion = new FacilityConfigVersionEntity();
        rollbackVersion.setFacility(facility);
        rollbackVersion.setTenantId(tenantId);
        rollbackVersion.setVersion(nextVersion);
        rollbackVersion.setConfigData(targetVersionEntity.getConfigData());
        rollbackVersion.setActive(true);
        rollbackVersion.setCreatedBy(actorId);
        rollbackVersion.setRollbackFrom(targetVersion);
        rollbackVersion = configVersionRepository.save(rollbackVersion);

        // Audit
        auditConfigChange("FACILITY", facilityId.toString(), tenantId, "ROLLBACK",
                oldConfig, targetVersionEntity.getConfigData(), actorId);

        // Outbox event
        publishEvent("CONFIG", facilityId.toString(), "tuso.config.rolledback",
                String.format("{\"facilityId\":%d,\"tenantId\":\"%s\",\"version\":%d,\"rollbackFrom\":%d,\"action\":\"CONFIG_ROLLEDBACK\"}",
                        facilityId, tenantId, nextVersion, targetVersion));

        log.info("Config for facility {} rolled back from version {} to new version {}", facilityId, targetVersion, nextVersion);
        return toResponse(rollbackVersion);
    }

    private void auditConfigChange(String entityType, String entityId, UUID tenantId,
                                    String action, Map<String, Object> oldConfig,
                                    Map<String, Object> newConfig, String actorId) {
        ConfigAuditEntity audit = new ConfigAuditEntity();
        audit.setEntityType(entityType);
        audit.setEntityId(entityId);
        audit.setTenantId(tenantId);
        audit.setAction(action);
        audit.setOldConfig(oldConfig);
        audit.setNewConfig(newConfig);
        audit.setActorId(actorId);
        configAuditRepository.save(audit);
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

    private ConfigVersionResponse toResponse(FacilityConfigVersionEntity entity) {
        return new ConfigVersionResponse(
                entity.getVersion(),
                entity.getConfigData(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getRollbackFrom()
        );
    }
}
