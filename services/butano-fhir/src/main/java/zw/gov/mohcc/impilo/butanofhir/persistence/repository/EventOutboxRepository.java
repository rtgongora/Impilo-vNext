package zw.gov.mohcc.impilo.butanofhir.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.butanofhir.persistence.entity.EventOutboxEntity;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    List<EventOutboxEntity> findByPublishedFalseOrderByCreatedAtAsc();
}
