package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.MissionEventEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface MissionEventRepository extends JpaRepository<MissionEventEntity, UUID> {
    List<MissionEventEntity> findByIncidentIdOrderByOccurredAtAsc(UUID incidentId);
}
