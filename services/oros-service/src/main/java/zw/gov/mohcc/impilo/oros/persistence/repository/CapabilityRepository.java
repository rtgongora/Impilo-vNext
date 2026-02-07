package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.persistence.entity.CapabilityEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CapabilityRepository extends JpaRepository<CapabilityEntity, UUID> {

    Optional<CapabilityEntity> findByTenantIdAndFacilityIdAndOrderType(
            UUID tenantId, UUID facilityId, OrderType orderType);

    Optional<CapabilityEntity> findByTenantIdAndFacilityIdIsNullAndOrderType(
            UUID tenantId, OrderType orderType);

    List<CapabilityEntity> findByTenantIdAndActiveTrue(UUID tenantId);

    /**
     * Finds all capabilities for a tenant and order type (regardless of facility).
     * Used by adapters as a fallback when no facility-specific endpoint is configured.
     *
     * @param tenantId  the tenant UUID
     * @param orderType the order type
     * @return list of matching capability entities
     */
    List<CapabilityEntity> findByTenantIdAndOrderType(UUID tenantId, OrderType orderType);
}
