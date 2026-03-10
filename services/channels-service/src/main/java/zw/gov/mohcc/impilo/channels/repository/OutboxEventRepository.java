package zw.gov.mohcc.impilo.channels.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.channels.domain.OutboxEventEntity;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    List<OutboxEventEntity> findByAggregateId(String aggregateId);
}
