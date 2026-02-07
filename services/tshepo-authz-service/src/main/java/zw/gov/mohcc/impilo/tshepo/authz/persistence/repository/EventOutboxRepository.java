package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.EventOutboxEntity;

import java.util.List;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    /**
     * Find the oldest unpublished outbox entries (batch of 100).
     */
    @Query("SELECT e FROM EventOutboxEntity e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC LIMIT 100")
    List<EventOutboxEntity> findUnpublished();
}
