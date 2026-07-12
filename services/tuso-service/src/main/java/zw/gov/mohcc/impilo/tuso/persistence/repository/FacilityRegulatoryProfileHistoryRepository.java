package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileHistoryEntity;

import java.util.List;

public interface FacilityRegulatoryProfileHistoryRepository
        extends JpaRepository<FacilityRegulatoryProfileHistoryEntity, Long> {

    List<FacilityRegulatoryProfileHistoryEntity> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);
}
