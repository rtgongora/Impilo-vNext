package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ResuscitationEventEntity;

import java.util.List;
import java.util.UUID;

public interface ResuscitationEventRepository extends JpaRepository<ResuscitationEventEntity, UUID> {
    List<ResuscitationEventEntity> findByActivationIdOrderByRecordedAtAsc(UUID activationId);
    List<ResuscitationEventEntity> findByTraumaEpisodeIdOrderByRecordedAtAsc(UUID traumaEpisodeId);
}
