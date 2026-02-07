package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.domain.CapabilityMode;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CapabilityEntity;

import java.util.UUID;

/**
 * Service for resolving the integration capability mode for a tenant/facility.
 *
 * <p>Determines whether inventory operations should be handled internally,
 * routed to an external adapter, or operate in hybrid mode (dual-write with
 * reconciliation). Resolution order: facility-level first, then tenant-level
 * fallback, then default to INTERNAL.</p>
 */
public interface CapabilityRoutingService {

    /**
     * Resolve the effective capability mode for a tenant and facility.
     *
     * <p>Lookup order: facility-specific override, then tenant-level default,
     * then fall back to {@link CapabilityMode#INTERNAL}.</p>
     *
     * @param tenantId   the tenant identifier
     * @param facilityId the facility identifier
     * @return the resolved capability mode
     */
    CapabilityMode getMode(UUID tenantId, UUID facilityId);

    /**
     * Check if the resolved mode includes internal processing.
     *
     * @param tenantId   the tenant identifier
     * @param facilityId the facility identifier
     * @return true if mode is INTERNAL or HYBRID
     */
    boolean isInternal(UUID tenantId, UUID facilityId);

    /**
     * Check if the resolved mode includes external adapter routing.
     *
     * @param tenantId   the tenant identifier
     * @param facilityId the facility identifier
     * @return true if mode is ADAPTER or HYBRID
     */
    boolean isAdapter(UUID tenantId, UUID facilityId);

    /**
     * Create or update the capability mode for a tenant/facility combination.
     *
     * @param tenantId   the tenant identifier
     * @param facilityId the facility identifier (nullable for tenant-level default)
     * @param mode       the capability mode to set
     * @return the persisted capability entity
     */
    CapabilityEntity setMode(UUID tenantId, UUID facilityId, CapabilityMode mode);
}
