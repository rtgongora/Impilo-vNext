package zw.gov.mohcc.impilo.community.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import zw.gov.mohcc.impilo.community.persistence.entity.EventOutboxEntity;

import java.util.List;

public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    @Query("SELECT e FROM EventOutboxEntity e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC")
    List<EventOutboxEntity> findUnpublished(Pageable pageable);
}
