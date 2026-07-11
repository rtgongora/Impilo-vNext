package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionResponseRepository extends JpaRepository<InspectionResponseEntity, UUID> {
    List<InspectionResponseEntity> findByVisitIdOrderByItemCodeAsc(UUID visitId);
    Optional<InspectionResponseEntity> findByVisitIdAndItemCode(UUID visitId, String itemCode);
    long countByVisitIdAndResponse(UUID visitId, String response);
    long countByVisitId(UUID visitId);
}
