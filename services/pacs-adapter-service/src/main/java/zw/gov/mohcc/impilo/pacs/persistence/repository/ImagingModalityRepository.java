package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingModalityEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImagingModalityRepository extends JpaRepository<ImagingModalityEntity, Long> {

    List<ImagingModalityEntity> findByTenantIdAndFacilityIdAndActiveTrueOrderByNameAsc(UUID tenantId, String facilityId);

    List<ImagingModalityEntity> findByTenantIdAndFacilityIdOrderByNameAsc(UUID tenantId, String facilityId);

    Optional<ImagingModalityEntity> findByTenantIdAndFacilityIdAndAeTitle(UUID tenantId, String facilityId, String aeTitle);
}
