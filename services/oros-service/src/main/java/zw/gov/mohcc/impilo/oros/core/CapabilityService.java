package zw.gov.mohcc.impilo.oros.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.AdapterMode;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.CapabilityEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.CapabilityRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages tenant/facility capability configuration for order types.
 */
@Service
public class CapabilityService {

    private static final Logger log = LoggerFactory.getLogger(CapabilityService.class);

    private final CapabilityRepository capabilityRepository;

    public CapabilityService(CapabilityRepository capabilityRepository) {
        this.capabilityRepository = capabilityRepository;
    }

    /**
     * Get a specific capability by facility and order type.
     */
    public Optional<CapabilityEntity> getCapability(UUID facilityId, OrderType orderType) {
        TrustContext ctx = TrustContextHolder.require();
        return capabilityRepository.findByTenantIdAndFacilityIdAndOrderType(
                ctx.tenantId(), facilityId, orderType);
    }

    /**
     * List all active capabilities for the current tenant.
     */
    public List<CapabilityEntity> listCapabilities() {
        TrustContext ctx = TrustContextHolder.require();
        return capabilityRepository.findByTenantIdAndActiveTrue(ctx.tenantId());
    }

    /**
     * Create or update a capability configuration.
     */
    @Transactional
    public CapabilityEntity upsertCapability(UUID facilityId, OrderType orderType,
                                              AdapterMode adapterMode, String externalEndpoint,
                                              String config) {
        TrustContext ctx = TrustContextHolder.require();

        Optional<CapabilityEntity> existing = capabilityRepository
                .findByTenantIdAndFacilityIdAndOrderType(ctx.tenantId(), facilityId, orderType);

        CapabilityEntity capability;
        if (existing.isPresent()) {
            capability = existing.get();
            capability.setAdapterMode(adapterMode);
            capability.setExternalEndpoint(externalEndpoint);
            capability.setConfig(config);
        } else {
            capability = new CapabilityEntity();
            capability.setTenantId(ctx.tenantId());
            capability.setFacilityId(facilityId);
            capability.setOrderType(orderType);
            capability.setAdapterMode(adapterMode);
            capability.setExternalEndpoint(externalEndpoint);
            capability.setConfig(config);
        }

        capability = capabilityRepository.save(capability);
        log.info("Capability upserted: facility={}, orderType={}, mode={}",
                facilityId, orderType, adapterMode);
        return capability;
    }
}
