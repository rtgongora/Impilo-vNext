package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.LearningOutboxEntity;

public interface LearningOutboxRepository extends JpaRepository<LearningOutboxEntity, UUID> {

    /**
     * Batch fetch of unpublished outbox rows for the scheduled Kafka publisher.
     *
     * <p>Bounded to 100 rows per call to keep the publish transaction short and
     * to mirror the canonical pattern used by {@code pct-service}'s
     * {@code EventOutboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc}.</p>
     */
    List<LearningOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
