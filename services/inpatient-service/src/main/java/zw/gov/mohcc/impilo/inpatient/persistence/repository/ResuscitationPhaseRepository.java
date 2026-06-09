package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ResuscitationPhaseEntity;

import java.util.List;
import java.util.UUID;

public interface ResuscitationPhaseRepository extends JpaRepository<ResuscitationPhaseEntity, UUID> {
    List<ResuscitationPhaseEntity> findByActivationIdOrderByStartedAtAsc(UUID activationId);
}
