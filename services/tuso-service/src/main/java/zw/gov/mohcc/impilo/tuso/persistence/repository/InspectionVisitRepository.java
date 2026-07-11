package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionVisitEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionVisitRepository extends JpaRepository<InspectionVisitEntity, UUID> {
    List<InspectionVisitEntity> findByInspectionIdOrderByVisitNumberAsc(UUID inspectionId);
    Optional<InspectionVisitEntity> findFirstByInspectionIdOrderByVisitNumberDesc(UUID inspectionId);
}
