package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.FacilityImagingCapabilityEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityImagingCapabilityRepository extends JpaRepository<FacilityImagingCapabilityEntity, Long> {

    Optional<FacilityImagingCapabilityEntity> findByTenantIdAndFacilityId(UUID tenantId, String facilityId);

    List<FacilityImagingCapabilityEntity> findByTenantIdOrderByFacilityNameAsc(UUID tenantId);

    List<FacilityImagingCapabilityEntity> findByTenantIdAndDeploymentModeOrderByFacilityNameAsc(UUID tenantId, String deploymentMode);
}
