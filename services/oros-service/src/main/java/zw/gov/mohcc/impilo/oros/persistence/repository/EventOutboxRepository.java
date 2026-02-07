package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;

import java.util.List;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    List<EventOutboxEntity> findByPublishedAtIsNullOrderByCreatedAtAsc();

    /**
     * Finds up to 100 unpublished events ordered by creation time.
     * Used by the OutboxPublisher for batch processing.
     *
     * @return list of up to 100 unpublished outbox events
     */
    List<EventOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
