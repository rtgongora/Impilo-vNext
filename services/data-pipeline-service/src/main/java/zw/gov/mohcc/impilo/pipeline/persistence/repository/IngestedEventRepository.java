package zw.gov.mohcc.impilo.pipeline.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pipeline.persistence.entity.IngestedEventEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngestedEventRepository extends JpaRepository<IngestedEventEntity, String> {

    Optional<IngestedEventEntity> findByTenantIdAndEventId(UUID tenantId, String eventId);

    boolean existsByEventId(String eventId);

    long countByTenantIdAndSourceId(UUID tenantId, String sourceId);
}
