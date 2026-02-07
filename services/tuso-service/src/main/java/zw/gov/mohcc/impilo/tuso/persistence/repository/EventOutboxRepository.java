package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;

import java.util.List;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {

    List<EventOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    Page<EventOutboxEntity> findByAggregateTypeOrderByCreatedAtDesc(String aggregateType, Pageable pageable);

    Page<EventOutboxEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
