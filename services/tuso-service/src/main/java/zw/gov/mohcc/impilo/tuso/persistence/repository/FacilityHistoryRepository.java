package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityHistoryEntity;

@Repository
public interface FacilityHistoryRepository extends JpaRepository<FacilityHistoryEntity, Long> {

    Page<FacilityHistoryEntity> findByFacilityIdOrderByChangedAtDesc(Long facilityId, Pageable pageable);

    Page<FacilityHistoryEntity> findByFacilityIdAndChangeTypeOrderByChangedAtDesc(
            Long facilityId, String changeType, Pageable pageable);
}
