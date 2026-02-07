package zw.gov.mohcc.impilo.tshepo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    @Query("SELECT e FROM EventOutboxEntity e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC")
    List<EventOutboxEntity> findUnpublished();
}
