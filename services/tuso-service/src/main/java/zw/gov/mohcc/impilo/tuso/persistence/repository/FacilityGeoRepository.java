package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGeoEntity;

import java.util.Optional;

@Repository
public interface FacilityGeoRepository extends JpaRepository<FacilityGeoEntity, Long> {

    Optional<FacilityGeoEntity> findByFacilityId(Long facilityId);
}
