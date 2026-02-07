package zw.gov.mohcc.impilo.tshepo.consent.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    /**
     * Find all unpublished outbox events ordered by creation time.
     * Used by the outbox publisher to poll for pending events.
     */
    @Query("SELECT e FROM EventOutboxEntity e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC")
    List<EventOutboxEntity> findUnpublished();
}
