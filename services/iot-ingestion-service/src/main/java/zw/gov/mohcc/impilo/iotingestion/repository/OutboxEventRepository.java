package zw.gov.mohcc.impilo.iotingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.iotingestion.domain.OutboxEventEntity;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findByAggregateIdAndEventType(String aggregateId, String eventType);

    long countByAggregateId(String aggregateId);
}
