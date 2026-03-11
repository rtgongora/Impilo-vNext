package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.OutboxEventEntity;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findByAggregateIdAndEventType(String aggregateId, String eventType);

    long countByAggregateId(String aggregateId);
}
