package zw.gov.mohcc.impilo.butano.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.butano.persistence.entity.EventOutboxEntity;

import java.util.List;

/**
 * Spring Data JPA repository for {@link EventOutboxEntity}.
 *
 * <p>Supports the transactional outbox pattern. The primary query
 * retrieves the oldest 100 unpublished events for batch delivery
 * to Kafka by the {@link zw.gov.mohcc.impilo.butano.events.ButanoOutboxPublisher}.</p>
 */
@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    /**
     * Find the oldest 100 events that have not yet been published to Kafka.
     * Orders by creation time ascending so events are delivered in order.
     *
     * @return up to 100 unpublished outbox events
     */
    List<EventOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
