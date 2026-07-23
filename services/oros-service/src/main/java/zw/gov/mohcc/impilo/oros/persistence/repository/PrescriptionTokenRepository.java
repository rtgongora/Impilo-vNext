package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.oros.persistence.entity.PrescriptionTokenEntity;

import java.util.List;
import java.util.Optional;

public interface PrescriptionTokenRepository extends JpaRepository<PrescriptionTokenEntity, String> {

    Optional<PrescriptionTokenEntity> findByPrescriptionIdAndStatus(String prescriptionId, String status);

    List<PrescriptionTokenEntity> findByPrescriptionId(String prescriptionId);
}
