package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import zw.gov.mohcc.impilo.khuluma.domain.OutboxEventEntity;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.publishedAt IS NULL ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findUnpublished();
}
