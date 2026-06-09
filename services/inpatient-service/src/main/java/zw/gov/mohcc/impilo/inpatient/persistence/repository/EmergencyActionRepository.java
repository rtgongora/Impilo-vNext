package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EmergencyActionEntity;

import java.util.List;
import java.util.UUID;

public interface EmergencyActionRepository extends JpaRepository<EmergencyActionEntity, UUID> {
    List<EmergencyActionEntity> findByActivationIdOrderByPerformedAtDesc(UUID activationId);
    List<EmergencyActionEntity> findByActivationIdAndActionTypeOrderByPerformedAtDesc(UUID activationId, String actionType);
}
