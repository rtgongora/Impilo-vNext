package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.domain.CapabilityMode;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CapabilityEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.CapabilityRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Capability routing service implementation.
 *
 * <p>Resolves the integration mode for a given tenant/facility combination
 * using a two-tier lookup: facility-specific configuration overrides
 * tenant-level defaults, with a final fallback to INTERNAL mode.</p>
 *
 * <p>This determines how inventory operations are processed:
 * <ul>
 *   <li>INTERNAL: all operations handled by the inventory service</li>
 *   <li>ADAPTER: operations routed to an external supply-chain system</li>
 *   <li>HYBRID: dual-write with reconciliation between internal and external</li>
 * </ul>
 */
@Service
public class CapabilityRoutingServiceImpl implements CapabilityRoutingService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityRoutingServiceImpl.class);

    private final CapabilityRepository capabilityRepository;

    public CapabilityRoutingServiceImpl(CapabilityRepository capabilityRepository) {
        this.capabilityRepository = capabilityRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Facility-level: look up by tenantId + facilityId</li>
     *   <li>Tenant-level: look up by tenantId with null facilityId</li>
     *   <li>Default: {@link CapabilityMode#INTERNAL}</li>
     * </ol>
     */
    @Override
    public CapabilityMode getMode(UUID tenantId, UUID facilityId) {
        // 1. Facility-level lookup
        if (facilityId != null) {
            Optional<CapabilityEntity> facilityLevel =
                    capabilityRepository.findByTenantIdAndFacilityId(tenantId, facilityId);
            if (facilityLevel.isPresent()) {
                CapabilityMode mode = facilityLevel.get().getCapabilityMode();
                log.debug("Capability resolved (facility-level): tenant={}, facility={}, mode={}",
                        tenantId, facilityId, mode);
                return mode;
            }
        }

        // 2. Tenant-level fallback
        Optional<CapabilityEntity> tenantLevel =
                capabilityRepository.findByTenantIdAndFacilityIdIsNull(tenantId);
        if (tenantLevel.isPresent()) {
            CapabilityMode mode = tenantLevel.get().getCapabilityMode();
            log.debug("Capability resolved (tenant-level): tenant={}, mode={}", tenantId, mode);
            return mode;
        }

        // 3. Default to INTERNAL
        log.debug("Capability resolved (default): tenant={}, facility={}, mode=INTERNAL",
                tenantId, facilityId);
        return CapabilityMode.INTERNAL;
    }

    @Override
    public boolean isInternal(UUID tenantId, UUID facilityId) {
        CapabilityMode mode = getMode(tenantId, facilityId);
        return mode == CapabilityMode.INTERNAL || mode == CapabilityMode.HYBRID;
    }

    @Override
    public boolean isAdapter(UUID tenantId, UUID facilityId) {
        CapabilityMode mode = getMode(tenantId, facilityId);
        return mode == CapabilityMode.ADAPTER || mode == CapabilityMode.HYBRID;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Performs an upsert: if a capability record already exists for the
     * tenant/facility combination, its mode is updated. Otherwise a new
     * record is created.</p>
     */
    @Override
    @Transactional
    public CapabilityEntity setMode(UUID tenantId, UUID facilityId, CapabilityMode mode) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }

        // Look for existing capability
        Optional<CapabilityEntity> existing;
        if (facilityId != null) {
            existing = capabilityRepository.findByTenantIdAndFacilityId(tenantId, facilityId);
        } else {
            existing = capabilityRepository.findByTenantIdAndFacilityIdIsNull(tenantId);
        }

        CapabilityEntity capability;
        if (existing.isPresent()) {
            capability = existing.get();
            CapabilityMode previousMode = capability.getCapabilityMode();
            capability.setCapabilityMode(mode);
            capability = capabilityRepository.save(capability);
            log.info("Capability updated: tenant={}, facility={}, mode={} -> {}",
                    tenantId, facilityId, previousMode, mode);
        } else {
            capability = new CapabilityEntity();
            capability.setTenantId(tenantId);
            capability.setFacilityId(facilityId);
            capability.setCapabilityMode(mode);
            capability = capabilityRepository.save(capability);
            log.info("Capability created: tenant={}, facility={}, mode={}",
                    tenantId, facilityId, mode);
        }

        return capability;
    }
}
