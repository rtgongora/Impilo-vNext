package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AssistanceEventEntity;

import java.util.List;
import java.util.UUID;

public interface AssistanceEventRepository extends JpaRepository<AssistanceEventEntity, UUID> {

    List<AssistanceEventEntity> findByAssistanceIdOrderByOccurredAtAsc(UUID assistanceId);
}
