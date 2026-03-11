package zw.gov.mohcc.impilo.dataingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dataingestion.domain.DeadLetterEventEntity;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEventEntity, Long> {

    long countByTopicName(String topicName);
}
