package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEmergencyCapabilityEntity;

import java.util.Optional;
import java.util.UUID;

public interface FacilityEmergencyCapabilityRepository extends JpaRepository<FacilityEmergencyCapabilityEntity, Long> {
    Optional<FacilityEmergencyCapabilityEntity> findByTenantIdAndFacilityId(UUID tenantId, Long facilityId);
}
