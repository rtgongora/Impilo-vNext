package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.CapabilityProfileEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CapabilityProfileRepository extends JpaRepository<CapabilityProfileEntity, String> {
    Optional<CapabilityProfileEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId);
    Optional<CapabilityProfileEntity> findFirstByTenantId(UUID tenantId);
}
