package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityTrustDimensionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacilityTrustDimensionRepository
        extends JpaRepository<FacilityTrustDimensionEntity, Long> {

    List<FacilityTrustDimensionEntity> findByTenantIdAndFacilityUuidOrderByDimensionAsc(
            UUID tenantId, UUID facilityUuid);

    Optional<FacilityTrustDimensionEntity> findByTenantIdAndFacilityUuidAndDimension(
            UUID tenantId, UUID facilityUuid, String dimension);
}
