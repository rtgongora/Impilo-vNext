package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.oros.persistence.entity.PrescriptionVersionEntity;

import java.util.List;
import java.util.Optional;

public interface PrescriptionVersionRepository extends JpaRepository<PrescriptionVersionEntity, String> {

    Optional<PrescriptionVersionEntity> findFirstByPrescriptionIdOrderByVersionDesc(String prescriptionId);

    List<PrescriptionVersionEntity> findByPrescriptionIdOrderByVersionDesc(String prescriptionId);
}
