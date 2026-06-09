package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ResuscitationRecordEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResuscitationRecordRepository extends JpaRepository<ResuscitationRecordEntity, UUID> {
    Optional<ResuscitationRecordEntity> findFirstByActivationIdOrderByCreatedAtDesc(UUID activationId);
    List<ResuscitationRecordEntity> findByActivationIdOrderByCreatedAtDesc(UUID activationId);
}
