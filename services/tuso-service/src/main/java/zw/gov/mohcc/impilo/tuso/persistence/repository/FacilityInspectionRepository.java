package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityInspectionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityInspectionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FacilityInspectionRepository extends JpaRepository<FacilityInspectionEntity, UUID> {
    List<FacilityInspectionEntity> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);
    List<FacilityInspectionEntity> findByApplicationApplicationIdOrderByCreatedAtDesc(UUID applicationId);
    List<FacilityInspectionEntity> findByFacilityIdAndStatus(Long facilityId, FacilityInspectionStatus status);
    List<FacilityInspectionEntity> findByNextInspectionDueDateBefore(LocalDate dueDate);
}
