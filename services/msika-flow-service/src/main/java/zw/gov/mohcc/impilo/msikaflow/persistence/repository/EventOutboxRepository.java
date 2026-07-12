package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.EventOutboxEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutboxEntity, Long> {
    List<EventOutboxEntity> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
    Page<EventOutboxEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
