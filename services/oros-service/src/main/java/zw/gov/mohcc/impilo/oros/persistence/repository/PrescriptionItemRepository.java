package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.oros.persistence.entity.PrescriptionItemEntity;

import java.util.List;
import java.util.UUID;

public interface PrescriptionItemRepository extends JpaRepository<PrescriptionItemEntity, UUID> {

    List<PrescriptionItemEntity> findByPrescriptionVersionId(String prescriptionVersionId);
}
