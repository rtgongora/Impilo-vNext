package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityReadinessEntity;

import java.util.Optional;

@Repository
public interface FacilityReadinessRepository extends JpaRepository<FacilityReadinessEntity, Long> {

    Optional<FacilityReadinessEntity> findByFacility_Id(Long facilityId);
}
