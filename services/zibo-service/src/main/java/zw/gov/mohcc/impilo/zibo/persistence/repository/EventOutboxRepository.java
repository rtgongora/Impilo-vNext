package zw.gov.mohcc.impilo.zibo.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.zibo.persistence.entity.EventOutboxEntity;

import java.util.List;

/**
 * Repository for {@link EventOutboxEntity} persistence operations.
 * Used by the outbox polling publisher to find unpublished events
 * and mark them as published after successful Kafka delivery.
 */
@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    /**
     * Finds up to 100 unpublished events ordered by creation time (oldest first).
     * Used by the polling publisher to batch-send events to Kafka.
     *
     * @return list of up to 100 unpublished outbox events
     */
    List<EventOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
