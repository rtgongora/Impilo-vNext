package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitEntity;

import java.util.List;

public interface FacilityUnitRepository extends JpaRepository<FacilityUnitEntity, Long> {
    List<FacilityUnitEntity> findByFacilityIdOrderByCreatedAtAsc(Long facilityId);
}
