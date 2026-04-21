package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionFindingEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionFindingStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RectificationStatus;

import java.util.List;
import java.util.UUID;

public interface InspectionFindingRepository extends JpaRepository<InspectionFindingEntity, UUID> {
    List<InspectionFindingEntity> findByInspectionInspectionIdOrderByCreatedAtAsc(UUID inspectionId);
    List<InspectionFindingEntity> findByInspectionFacilityIdAndStatus(Long facilityId, InspectionFindingStatus status);
    List<InspectionFindingEntity> findByInspectionApplicationApplicationIdAndRectificationStatus(UUID applicationId, RectificationStatus rectificationStatus);
}
