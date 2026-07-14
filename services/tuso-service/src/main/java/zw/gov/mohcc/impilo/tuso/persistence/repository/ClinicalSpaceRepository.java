package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ClinicalSpaceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicalSpaceRepository extends JpaRepository<ClinicalSpaceEntity, UUID> {

    Optional<ClinicalSpaceEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<ClinicalSpaceEntity> findByTenantIdAndSpaceTypeOrderByNameAsc(UUID tenantId, String spaceType);

    List<ClinicalSpaceEntity> findByTenantIdOrderByNameAsc(UUID tenantId);

    List<ClinicalSpaceEntity> findByTenantIdAndFacilityIdOrderByNameAsc(UUID tenantId, Long facilityId);
}
