package zw.gov.mohcc.impilo.connectorfhir.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.connectorfhir.domain.OutboxEventEntity;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
    List<OutboxEventEntity> findByAggregateIdAndEventType(String aggregateId, String eventType);
    long countByAggregateId(String aggregateId);
}
