package zw.gov.mohcc.impilo.pharmacy.elmis.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.elmis.persistence.entity.EventOutboxEntity;

import java.util.List;

/**
 * Repository for the event outbox (reliable Kafka publishing).
 */
@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    List<EventOutboxEntity> findByPublishedFalseOrderByCreatedAtAsc();
}
