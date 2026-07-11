package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCapabilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityReadinessEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCapabilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityReadinessRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Curated writes for facility capabilities ("services offered" registry truth)
 * and readiness (infrastructure assessment). Until now these were create-time /
 * repository-only — the configuration console could read but never correct them.
 *
 * <p>Capabilities are never deleted; deactivation preserves history. ZIBO
 * validation is a governed integration verdict — not writable here.</p>
 */
@Service
public class FacilityCapabilityReadinessService {

    private static final Logger log = LoggerFactory.getLogger(FacilityCapabilityReadinessService.class);

    private final FacilityRepository facilityRepository;
    private final FacilityCapabilityRepository capabilityRepository;
    private final FacilityReadinessRepository readinessRepository;

    public FacilityCapabilityReadinessService(FacilityRepository facilityRepository,
                                              FacilityCapabilityRepository capabilityRepository,
                                              FacilityReadinessRepository readinessRepository) {
        this.facilityRepository = facilityRepository;
        this.capabilityRepository = capabilityRepository;
        this.readinessRepository = readinessRepository;
    }

    public record CapabilityRequest(
            String capabilityCode, String capabilityType, String name,
            Boolean active, Map<String, Object> operatingHours, Map<String, Object> metadata) {}

    public record ReadinessRequest(
            String connectivity, String powerSource, Boolean powerBackup,
            Integer deviceCount, Boolean ehrReady, Map<String, Object> complianceFlags) {}

    @Transactional(readOnly = true)
    public List<FacilityCapabilityEntity> listCapabilities(TrustContext ctx, Long facilityId) {
        requireFacility(ctx, facilityId);
        return capabilityRepository.findByFacilityId(facilityId);
    }

    @Transactional
    public FacilityCapabilityEntity createCapability(TrustContext ctx, Long facilityId, CapabilityRequest req) {
        FacilityEntity facility = requireFacility(ctx, facilityId);
        if (req.capabilityCode() == null || req.capabilityCode().isBlank()) {
            throw new IllegalArgumentException("capabilityCode is required");
        }
        boolean duplicate = capabilityRepository.findByFacilityId(facilityId).stream()
                .anyMatch(c -> req.capabilityCode().equalsIgnoreCase(c.getCapabilityCode())
                        && Boolean.TRUE.equals(c.getActive()));
        if (duplicate) {
            throw new IllegalArgumentException("Facility already has active capability " + req.capabilityCode());
        }
        FacilityCapabilityEntity entity = new FacilityCapabilityEntity();
        entity.setFacility(facility);
        entity.setTenantId(facility.getTenantId());
        entity.setCapabilityCode(req.capabilityCode());
        entity.setCapabilityType(req.capabilityType() != null ? req.capabilityType() : "SERVICE");
        entity.setName(req.name() != null ? req.name() : req.capabilityCode());
        entity.setZiboValidated(false);
        entity.setActive(req.active() == null || req.active());
        entity.setOperatingHours(req.operatingHours());
        entity.setMetadata(req.metadata());
        FacilityCapabilityEntity saved = capabilityRepository.save(entity);
        log.info("Capability {} created for facility {} (actor={})",
                saved.getCapabilityCode(), facilityId, actor(ctx));
        return saved;
    }

    /** Partial update; null fields left unchanged. ziboValidated is never writable here. */
    @Transactional
    public FacilityCapabilityEntity updateCapability(TrustContext ctx, Long facilityId,
                                                     Long capabilityId, CapabilityRequest req) {
        requireFacility(ctx, facilityId);
        FacilityCapabilityEntity entity = requireCapability(facilityId, capabilityId);
        if (req.name() != null && !req.name().isBlank()) {
            entity.setName(req.name());
        }
        if (req.capabilityType() != null && !req.capabilityType().isBlank()) {
            entity.setCapabilityType(req.capabilityType());
        }
        if (req.active() != null) {
            entity.setActive(req.active());
        }
        if (req.operatingHours() != null) {
            entity.setOperatingHours(req.operatingHours());
        }
        if (req.metadata() != null) {
            entity.setMetadata(req.metadata());
        }
        FacilityCapabilityEntity saved = capabilityRepository.save(entity);
        log.info("Capability {} updated for facility {} (actor={})", capabilityId, facilityId, actor(ctx));
        return saved;
    }

    @Transactional
    public FacilityCapabilityEntity retireCapability(TrustContext ctx, Long facilityId, Long capabilityId) {
        requireFacility(ctx, facilityId);
        FacilityCapabilityEntity entity = requireCapability(facilityId, capabilityId);
        entity.setActive(false);
        FacilityCapabilityEntity saved = capabilityRepository.save(entity);
        log.info("Capability {} retired for facility {} (actor={})", capabilityId, facilityId, actor(ctx));
        return saved;
    }

    @Transactional(readOnly = true)
    public FacilityReadinessEntity getReadiness(TrustContext ctx, Long facilityId) {
        requireFacility(ctx, facilityId);
        return readinessRepository.findByFacilityId(facilityId).orElse(null);
    }

    /** Upsert: an honest assessment always overwrites the derived baseline. */
    @Transactional
    public FacilityReadinessEntity upsertReadiness(TrustContext ctx, Long facilityId, ReadinessRequest req) {
        FacilityEntity facility = requireFacility(ctx, facilityId);
        FacilityReadinessEntity entity = readinessRepository.findByFacilityId(facilityId)
                .orElseGet(() -> {
                    FacilityReadinessEntity fresh = new FacilityReadinessEntity();
                    fresh.setFacility(facility);
                    return fresh;
                });
        if (req.connectivity() != null) {
            entity.setConnectivity(req.connectivity());
        }
        if (req.powerSource() != null) {
            entity.setPowerSource(req.powerSource());
        }
        if (req.powerBackup() != null) {
            entity.setPowerBackup(req.powerBackup());
        }
        if (req.deviceCount() != null) {
            entity.setDeviceCount(req.deviceCount());
        }
        if (req.ehrReady() != null) {
            entity.setEhrReady(req.ehrReady());
        }
        if (req.complianceFlags() != null) {
            entity.setComplianceFlags(req.complianceFlags());
        }
        entity.setAssessedAt(Instant.now());
        entity.setAssessedBy(actor(ctx));
        FacilityReadinessEntity saved = readinessRepository.save(entity);
        log.info("Readiness assessed for facility {} (actor={})", facilityId, actor(ctx));
        return saved;
    }

    private FacilityEntity requireFacility(TrustContext ctx, Long facilityId) {
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));
        if (ctx != null && !facility.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation: facility belongs to different tenant");
        }
        return facility;
    }

    private FacilityCapabilityEntity requireCapability(Long facilityId, Long capabilityId) {
        FacilityCapabilityEntity entity = capabilityRepository.findById(capabilityId)
                .orElseThrow(() -> new IllegalArgumentException("Capability not found: " + capabilityId));
        if (entity.getFacility() == null || !entity.getFacility().getId().equals(facilityId)) {
            throw new IllegalArgumentException("Capability " + capabilityId + " does not belong to facility " + facilityId);
        }
        return entity;
    }

    private static String actor(TrustContext ctx) {
        return ctx != null && ctx.actorId() != null ? ctx.actorId() : "system";
    }
}
