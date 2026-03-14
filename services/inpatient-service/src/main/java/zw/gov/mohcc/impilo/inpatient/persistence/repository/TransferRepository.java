package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.TransferEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<TransferEntity, Long> {

    List<TransferEntity> findByAdmissionRefOrderByTransferredAtDesc(UUID admissionRef);
}
