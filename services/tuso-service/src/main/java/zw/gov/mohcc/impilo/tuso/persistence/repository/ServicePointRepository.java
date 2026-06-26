package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ServicePointEntity;

import java.util.List;
import java.util.UUID;

public interface ServicePointRepository extends JpaRepository<ServicePointEntity, UUID> {
    List<ServicePointEntity> findByFacilityIdAndActiveTrueOrderByCreatedAtAsc(Long facilityId);
    List<ServicePointEntity> findByFacilityUnitIdAndActiveTrueOrderByCreatedAtAsc(Long facilityUnitId);
    long countByFacilityIdAndActiveTrue(Long facilityId);
}
