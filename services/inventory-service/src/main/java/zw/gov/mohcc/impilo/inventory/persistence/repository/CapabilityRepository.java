package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CapabilityEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CapabilityRepository extends JpaRepository<CapabilityEntity, UUID> {

    Optional<CapabilityEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);

    Optional<CapabilityEntity> findByTenantIdAndFacilityIdIsNull(UUID tenantId);
}
