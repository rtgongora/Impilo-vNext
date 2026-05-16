package zw.gov.mohcc.impilo.pacs.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pacs.persistence.entity.EventOutboxEntity;

import java.util.List;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    List<EventOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    List<EventOutboxEntity> findTop100ByPublishErrorIsNotNullOrderByCreatedAtDesc();
}
