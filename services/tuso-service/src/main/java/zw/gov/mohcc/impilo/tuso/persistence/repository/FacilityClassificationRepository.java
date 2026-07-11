package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityClassificationEntity;

import java.util.List;
import java.util.UUID;

public interface FacilityClassificationRepository extends JpaRepository<FacilityClassificationEntity, UUID> {
    List<FacilityClassificationEntity> findByActiveTrueOrderByClassCodeAscFacilityTypeLabelAsc();
}
