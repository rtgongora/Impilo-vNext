package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySetupStateEntity;

import java.util.Optional;

public interface FacilitySetupStateRepository extends JpaRepository<FacilitySetupStateEntity, Long> {
    Optional<FacilitySetupStateEntity> findByFacilityId(Long facilityId);
}
