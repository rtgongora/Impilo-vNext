package zw.gov.mohcc.impilo.mvumo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    List<EventOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
