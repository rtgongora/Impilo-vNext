package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityStatusHistoryEntity;

import java.util.List;

public interface FacilityStatusHistoryRepository extends JpaRepository<FacilityStatusHistoryEntity, Long> {
    List<FacilityStatusHistoryEntity> findByFacilityIdOrderByChangedAtDesc(Long facilityId);
}
