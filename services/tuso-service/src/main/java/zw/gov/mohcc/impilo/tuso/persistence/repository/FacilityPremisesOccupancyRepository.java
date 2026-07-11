package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityPremisesOccupancyEntity;

import java.util.List;
import java.util.UUID;

public interface FacilityPremisesOccupancyRepository extends JpaRepository<FacilityPremisesOccupancyEntity, UUID> {
    List<FacilityPremisesOccupancyEntity> findByPremisesIdAndEffectiveToIsNull(UUID premisesId);
    List<FacilityPremisesOccupancyEntity> findByFacilityIdAndEffectiveToIsNull(Long facilityId);
    List<FacilityPremisesOccupancyEntity> findByFacilityId(Long facilityId);
}
