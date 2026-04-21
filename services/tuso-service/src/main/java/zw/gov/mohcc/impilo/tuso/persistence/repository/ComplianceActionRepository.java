package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ComplianceActionEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RectificationStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ComplianceActionRepository extends JpaRepository<ComplianceActionEntity, UUID> {
    List<ComplianceActionEntity> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);
    List<ComplianceActionEntity> findByInspectionFindingInspectionInspectionIdOrderByCreatedAtAsc(UUID inspectionId);
    List<ComplianceActionEntity> findByFacilityIdAndStatus(Long facilityId, RectificationStatus status);
    List<ComplianceActionEntity> findByStatusAndDueDateBefore(RectificationStatus status, LocalDate date);
}
